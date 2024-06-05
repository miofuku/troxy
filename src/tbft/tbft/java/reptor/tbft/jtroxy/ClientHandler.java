package reptor.tbft.jtroxy;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.map.PushMessageEncoding;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.connect.BinaryUnbufferedConnector;
import reptor.distrbt.io.ssl.Ssl;
import reptor.distrbt.io.ssl.SslState;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.BFTReplyPhaseClient;
import reptor.tbft.TroxyClientResults;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.TreeMap;


class ClientHandler extends NetworkConnector implements PushMessageSink<NetworkMessage>
{

    private static final Logger s_logger = LoggerFactory.getLogger( ClientHandler.class );

    private final byte                      m_repno;
    private final short                     m_clino;

    private final PushMessageEncoding       m_msgenc;
    private final Ssl                       m_ssl;

    private final BidirectionalCertifier    m_propcertif;
    private final MessageMapper             m_mapper;

    private final FixedSlidingWindow<BFTReplyPhaseClient>   m_invwnd;
    // TODO: Use pull.
    private final Queue<NetworkMessage>     m_outqueue = new ArrayDeque<>();

    private TroxyClientResults              m_results;

    private long                            m_nextinvno = BFTInvocation.FIRST_INVOCATION;
    private boolean                         m_iscontact = false;

    private boolean                         useCache    = false;
    private Cache                           cache       = null;
    private Map<Long, Request>              pending     = new TreeMap<>();
    private long                            nextres     = 1L;
    private Queue<Long>                     complete    = new PriorityQueue<>();
    private Map<Long, Request>              requests    = new HashMap<>();


    public ClientHandler(SelectorDomainContext domcntxt, short clino,
                         MessageMapper mapper, BidirectionalCertifier propcertif, VerifierGroup replverif,
                         boolean enablessl, int sendbufsize, int recvbufsize,
                         byte repno, BFTInvocation invoke, Cache cache)
    {
        m_repno = repno;
        m_clino = clino;

        m_msgenc = new PushMessageEncoding( mapper, (int) clino, null );

        ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> conn;

        if( !enablessl )
        {
            m_ssl = null;
            conn  = m_msgenc;
        }
        else
        {
            m_ssl = new Ssl( domcntxt, null );
            m_ssl.adjustBuffer( recvbufsize, sendbufsize );
            conn  = new BinaryUnbufferedConnector( m_ssl, m_msgenc );
        }

        initEndpoint( domcntxt, conn );

        m_msgenc.getInbound().initReceiver( this );

        m_propcertif = propcertif;
        m_mapper     = mapper;
        m_invwnd     = new FixedSlidingWindow<>( BFTReplyPhaseClient.class, invoke.getInvocationWindowSize(),
                                                 i -> new BFTReplyPhaseClient( m_mapper, invoke.getReplicaGroup(), replverif, invoke ), m_nextinvno );
        m_invwnd.forEach( (invno, slot) -> slot.initInvocation() );

        useCache    = invoke.isUseCache();
        if (useCache)
            this.cache = cache;
    }


    @Override
    public String toString()
    {
        return String.format( "JTROXYCLI[%05d]", m_clino );
    }


    public void installSslState(SslState sslstate)
    {
        Preconditions.checkState( ( m_ssl==null )==( sslstate==null ) );

        if( m_ssl!=null )
            m_ssl.installState( sslstate );
    }


    public void init(TroxyClientResults results)
    {
        m_results = results;

        initNetworkResults( results );
    }


    public TroxyClientResults open()
    {
        if( m_ssl!=null )
            m_ssl.activate();

        m_msgenc.activate();

        m_iscontact = true;

        return m_results;
    }


    public int getInboundMinimumBufferSize()
    {
        return m_ssl==null ? 0 : m_ssl.getInboundConnect().getMinimumBufferSize();
    }


    public int getOutboundMinimumBufferSize()
    {
        return m_ssl==null ? 0 : m_ssl.getOutboundConnect().getMinimumBufferSize();
    }


    public TroxyClientResults processInboundData(ByteBuffer src) throws IOException
    {
        m_inbound.processData( src );

        return m_results;
    }


    // TODO: Directly via results?
    public TroxyClientResults retrieveOutboundData(ByteBuffer dst) throws IOException
    {
        m_outbound.retrieveData( dst );

        return m_results;
    }


    private boolean sendMessage(NetworkMessage msg)
    {
        if( m_mapper.writeMessageTo( m_results.getMessageBuffer(), msg ) )
            return true;
        else
        {
            m_outqueue.add( msg );
            m_results.setRequiredMessageBufferSize( msg.getMessageSize() );

            return false;
        }
    }


    public TroxyClientResults retrieveOutboundMessages()
    {
        NetworkMessage msg;

        while( ( msg = m_outqueue.poll() )!=null )
        {
            if( !sendMessage( msg ) )
                return m_results;
        }

        m_results.setRequiredMessageBufferSize( UnbufferedDataSource.NO_PENDING_DATA );

        return m_results;
    }


    // TODO: Received from client connection -> PULL
    @Override
    public void enqueueMessage(NetworkMessage msg)
    {
        handleClientRequest( (Request) msg );
    }


    private void handleClientRequest(Request request)
    {
        assert request.getNumber()<=m_nextinvno;

        prepareInvocation( m_nextinvno );

        Request surrequest = new Request( m_clino, m_nextinvno, request.getCommand(), request.isReadRequest(), false );

        if (useCache && surrequest.isReadRequest())
        {
            if (requests.putIfAbsent(surrequest.getNumber(), surrequest)==null)
            {
                if (!cache.hasRequest(surrequest))
                    surrequest = new Request(m_clino, m_nextinvno, request.getCommand(), request.isReadRequest(), false, false);
            }
        }
        else if (useCache && !surrequest.isReadRequest())
        {
            surrequest = new Request(m_clino, m_nextinvno, request.getCommand(), request.isReadRequest(), false, false);
            requests.putIfAbsent(surrequest.getNumber(), surrequest);
//            System.out.println("Receive write request, store: "+surrequest.toString());
        }

        surrequest.setValid();
        m_mapper.certifyAndSerializeMessage( surrequest, m_propcertif );

        m_nextinvno++;

        if (useCache && !surrequest.useReadOnlyOptimization() && surrequest.getNumber()>nextres)
        {
            pending.put(surrequest.getNumber(), surrequest);
            return;
        }

        sendMessage( surrequest );
    }


    public TroxyClientResults handleForwardedRequest(Data reqdata) throws VerificationException
    {
        Request request;

        try
        {
            request = (Request) m_mapper.readMessageFrom( reqdata );
        }
        catch( IOException e )
        {
            throw new VerificationException( e );
        }

        if( request.getSender()!=m_clino )
            throw new VerificationException();
//        if( request.getNumber()!=m_nextinvno )
//            throw new VerificationException( this + ": " + request + " when " + m_nextinvno + " expected" );

        m_mapper.verifyMessage( request, m_propcertif );

        if (!request.useReadOnlyOptimization())
        {
            if (useCache)
            {
                if (!request.isReadRequest())
                {
                    requests.putIfAbsent(request.getNumber(), request);
//                    System.out.println("Forward write request, store: "+request.toString());
                }
            }

            if (request.getNumber()<=m_nextinvno)
            {
                prepareInvocation(m_nextinvno);
                m_nextinvno++;
            }
        }
        else
        {
            if (useCache)
            {
                Integer hashReply = cache.getReply(request);
                if (hashReply != null)
                {
                    Reply reply = new Reply(m_repno, request.getSender(), request.getNumber(), true, true, ImmutableData.wrap(ByteBuffer.allocate(4).putInt(hashReply).array()), (byte) -1, null);
                    m_mapper.certifyAndSerializeMessage( reply, m_propcertif );
                    sendMessage(reply);
                    m_results.setLastFinishedInvocation(request.getNumber());
                }
                else
                {
                    Reply reply = new Reply(m_repno, request.getSender(), request.getNumber(), true, true, ImmutableData.wrap(ByteBuffer.allocate(4).putInt(-1).array()), (byte) -1, null);
                    m_mapper.certifyAndSerializeMessage( reply, m_propcertif );
                    sendMessage(reply);
                }
            }
        }

        return m_results;
    }


    public TroxyClientResults handleRequestExecuted(long invno, ImmutableData result, boolean replyfull) throws VerificationException
    {
        if (useCache)
        {
            if (!replyfull)
            {
                if (requests.containsKey(invno))
                {
                    Request req = requests.get(invno);
                    if (!req.isReadRequest())
                    {
                        cache.clearCache(req);
                        requests.remove(invno);
//                        System.out.println("Found write request, delete cache: "+req.toString());
                    }
                }
            }
        }

        if( m_invwnd.isBelowWindow( invno ) )
        {
            s_logger.debug( "{} execution of request {} is outdated; window starts at {}", this, invno, m_invwnd.getWindowStart() );
        }
        else if( m_iscontact )
        {
            Reply reply = createReply( invno, replyfull, result );

            handleReply( reply );
        }
        else
        {
//            Reply reply = replyfull ? createReply( invno, true, result ) :
//                                      createReply( invno, false, m_mapper.digestData( result ) );
            Reply reply = createReply( invno, replyfull, result );
            m_mapper.certifyAndSerializeMessage( reply, m_propcertif );

            sendMessage( reply );

            m_results.setLastFinishedInvocation( invno );
        }

        return m_results;
    }


    public TroxyClientResults handleReply(Data repdata) throws VerificationException
    {
        try
        {
            Reply reply = (Reply) m_mapper.readMessageFrom( repdata );

            if( reply.getRequester()!=m_clino )
                throw new VerificationException();

            return handleReply( reply );
        }
        catch( IOException e )
        {
            throw new VerificationException( e );
        }
    }


    private TroxyClientResults handleReply(Reply reply)
    {
        long invno = reply.getInvocationNumber();

        if( m_invwnd.isWithinWindow( invno ) )
        {
            BFTReplyPhaseClient replies = m_invwnd.getSlotUnchecked( invno );

            if( !reply.wasExecutedSpeculatively() && replies.handleReply( reply ) )
            {
                Reply clireply = createReply( invno, false, replies.getResult() );
                m_mapper.serializeMessage( clireply );

                m_msgenc.getOutbound().enqueueMessage( clireply );
                m_results.setLastFinishedInvocation( invno );

                if (useCache)
                {
                    if (requests.containsKey(clireply.getInvocationNumber()))
                    {
                        cache.insertReply(requests.get(reply.getInvocationNumber()), clireply);
                        requests.remove(clireply.getInvocationNumber());
                    }

                    checkResent(invno);
                }
            }
            else if ( useCache && reply.wasExecutedSpeculatively() )
            {
//                if (m_repno != 1)
//                {
                if ( replies.handleFastRead(reply, m_repno) || replies.getReads()==2 )
                {
                    if (cache.matchCache(requests.get(replies.getFastReadReply().getInvocationNumber()), replies.getFastReadReply()))
                    {
                        successfulFastRead(replies.getFastReadReply());
                    }
                    else
                    {
                        replies.initInvocation();
                        failedFastRead(reply);
                    }
                }
//                }
//                else
//                {
//                    if (cache.matchCache(requests.get(reply.getInvocationNumber()), reply))
//                    {
//                        successfulFastRead(reply);
//                    }
//                    else
//                    {
//                        replies.initInvocation();
//                        failedFastRead(reply);
//                    }
//                }
            }
        }

        return m_results;
    }


    private void successfulFastRead(Reply reply)
    {
        long invno = reply.getInvocationNumber();
        Reply clireply = createReply(invno, true, reply.getResult());
        m_mapper.serializeMessage(clireply);

        m_msgenc.getOutbound().enqueueMessage(clireply);
        m_results.setLastFinishedInvocation(invno);

        requests.remove(clireply.getInvocationNumber());
        checkResent(invno);
    }


    private void failedFastRead(Reply reply)
    {
        long invno = reply.getInvocationNumber();
        Request request = m_results.createWriteRequest(requests.get(reply.getInvocationNumber()), m_mapper, m_propcertif);

        if (invno == nextres)
        {
            sendMessage(request);
            nextres++;
            getResend(nextres);
        }
        else
        {
            pending.put(invno, request);
        }
        cache.clearCache(requests.get(reply.getInvocationNumber()));
    }


    private void checkResent(Long invno)
    {
        if (invno==nextres)
        {
            nextres++;
            getResend(nextres);
        }
        else if (invno > nextres)
        {
            complete.add(invno);
        }
    }


    private boolean clearComplete(long next)
    {
        while (complete.peek()!=null && complete.peek()==next)
        {
            complete.poll();
            next++;
            nextres++;
        }
        return true;
    }


    private void getResend(long current)
    {
        while (clearComplete(current) && pending.containsKey(nextres))
        {
            Request request = pending.get(nextres);
            Request re_request = m_results.createWriteRequest(request, m_mapper, m_propcertif);
            sendMessage(re_request);
            pending.remove(nextres);
            nextres++;
            current = nextres;
        }
    }


    private Reply createReply(long invno, boolean isfull, ImmutableData result)
    {
        Reply reply = new Reply( m_repno, m_clino, invno, true, isfull, result, (byte) -1, null );
        reply.setValid();

        return reply;
    }


    private void prepareInvocation(long invno)
    {
        if( invno>=m_invwnd.getWindowEnd() )
        {
            for( long current = m_invwnd.forwardWindow( invno - m_invwnd.size() + 1 ); current<=invno; current++ )
                m_invwnd.getSlotUnchecked( current ).initInvocation();
        }
    }

    public void addToRequests(Request req)
    {
        requests.putIfAbsent(req.getNumber(), req);
    }

}
