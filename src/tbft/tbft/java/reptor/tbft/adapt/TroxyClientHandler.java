package reptor.tbft.adapt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.com.PushMessageSource;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkConnection;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkTransmissionLayer;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.map.NetworkMessageSink;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.stage.LayerStage;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.ReplyMode;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyClientResults;
import reptor.tbft.TroxyNetworkResults;


public class TroxyClientHandler
        extends TroxyNetworkConnector
        implements NetworkTransmissionLayer<UnbufferedDataSink, UnbufferedDataSource, PushMessageSource<NetworkMessage>, NetworkMessageSink>,
                   PushMessageSource<NetworkMessage>
{

    private static final Logger s_logger = LoggerFactory.getLogger( TroxyClientHandler.class );

    private final short                 m_clino;
    private final TroxyClientResults    m_results;

    private PushMessageSink<? super NetworkMessage> m_receiver;

    private final MessageMapper                     m_mapper;
    private final Object                            m_mapcntxt;

    private final CommunicationStage<UnbufferedDataSink, PushMessageSource<NetworkMessage>> m_instage;
    private final CommunicationStage<NetworkMessageSink, UnbufferedDataSource>              m_outstage;

    private long                        startingtime = System.currentTimeMillis()/1000;
    private long                        isconflict = 0;
    private Set<Long>                   readset = new HashSet<>();


    public TroxyClientHandler(Troxy troxy, short clino, MessageMapper mapper, ByteBuffer msgoutbuf)
    {
        super( troxy );

        m_clino    = clino;
        m_mapper   = mapper;
        m_mapcntxt = mapper.createSourceContext( null, null );
        m_results  = new TroxyClientResults( msgoutbuf );
        m_instage  = new LayerStage<>( this, getInboundConnect(), this );
        m_outstage = new LayerStage<>( this, null, getOutboundConnect() );

        m_troxy.initClientHandler( clino, m_results );
    }


    @Override
    public String toString()
    {
        return String.format( "TROXYCLI[%05d]", m_clino );
    }


    @Override
    public CommunicationStage<UnbufferedDataSink, PushMessageSource<NetworkMessage>> getInboundStage()
    {
        return m_instage;
    }


    @Override
    public CommunicationStage<NetworkMessageSink, UnbufferedDataSource> getOutboundStage()
    {
        return m_outstage;
    }


    @Override
    public PushMessageSource<NetworkMessage> getInbound()
    {
        return this;
    }


    @Override
    public NetworkMessageSink getOutbound()
    {
        return null;
    }


    @Override
    public void activate()
    {
    }


    @Override
    public void deactivate()
    {
    }


    @Override
    public boolean isActivated()
    {
        return true;
    }


    @Override
    public void open(NetworkConnection<?, ?> conn, HandshakeState hsstate) throws IOException
    {
        SocketChannel channel = hsstate.getBufferedNetworkState().getNetworkState().getChannel();

        s_logger.debug( "{} open {}", this, channel );

        m_troxy.open( m_clino );

        processResult();
    }


    @Override
    public int getMinimumSinkBufferSize()
    {
        return m_troxy.getClientInboundMinimumBufferSize( m_clino );
    }


    @Override
    public int getMinimumSourceBufferSize()
    {
        return m_troxy.getClientOutboundMinimumBufferSize( m_clino );
    }


    @Override
    public void initReceiver(PushMessageSink<? super NetworkMessage> receiver)
    {
        m_receiver = receiver;
    }


    @Override
    protected TroxyNetworkResults processInboundData(ByteBuffer src) throws IOException
    {
        m_troxy.processClientInboundData( m_clino, src );

        return processClientResult();
    }


    @Override
    protected TroxyNetworkResults retrieveOutboundData(ByteBuffer dst) throws IOException
    {
        m_troxy.retrieveClientOutboundData( m_clino, dst );

        return processClientResult();
    }


    public void processForwardedRequest(Request request) throws VerificationException
    {
        assert request.getSender()==m_clino;

        s_logger.debug( "{} process forwarded {}", this, request );

        m_troxy.handleForwardedRequest( m_clino, request.getMessageData() );
        request.setValid();

        processResult();
    }


    public TroxyClientResults processRequestExecuted(RequestExecuted reqexecd) throws VerificationException
    {
        Request request = reqexecd.getRequest();

        if (reqexecd.wasExecutedSpeculatively())
        {
            readset.add(request.getNumber());
        }
        else if (request.isReadRequest())
        {
            if (readset.contains(request.getNumber()))
            {
                readset.remove(request.getNumber());
                isconflict++;
            }
        }

        assert request.getSender()==m_clino;
//        assert !reqexecd.wasExecutedSpeculatively();
        // Abuse replyfull to indicate read-only operation
        m_troxy.handleRequestExecuted( m_clino, request.getNumber(), reqexecd.getResult(),
//                                       reqexecd.getReplyMode()==ReplyMode.Full && reqexecd.wasExecutedSpeculatively() );
                                            reqexecd.wasExecutedSpeculatively() );

        return processResult();
    }


    public TroxyClientResults processReply(Reply reply) throws VerificationException
    {
        assert reply.getRequester()==m_clino;

        m_troxy.handleReply( m_clino, reply.getMessageData() );

        if ((System.currentTimeMillis()/1000-startingtime)%30==0)
            System.out.println("Client "+m_clino+" conflict rate: "+isconflict + " " +reply.getInvocationNumber());

        return processResult();
    }


    private TroxyClientResults processResult()
    {
        processNetworkResults( m_results );
        return processClientResult();
    }


    private TroxyClientResults processClientResult()
    {
        if( !m_results.hasMessageData() )
            return m_results;

        do
        {
            ByteBuffer buffer = m_results.startMessageDataProcessing();

            try
            {
                while( buffer.hasRemaining() )
                {
                    NetworkMessage msg = m_mapper.tryReadMessageFrom( buffer, m_mapcntxt );
                    msg.setValid();

                    assert msg!=null;

                    m_receiver.enqueueMessage( msg );
                }
            }
            catch( IOException e )
            {
                throw new IllegalStateException( e );
            }

            m_results.finishMessageDataProcessing();

            if( m_results.getRequiredMessageBufferSize()==UnbufferedDataSource.NO_PENDING_DATA )
                break;
            else
                m_troxy.retrieveOutboundMessages( m_clino );

        }
        while( true );

        return m_results;
    }

}
