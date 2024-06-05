package reptor.tbft.jtroxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.security.Key;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomain;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.mac.MacCertification;
import reptor.distrbt.certify.mac.MacProvider;
import reptor.distrbt.certify.mac.PlainSingleMacFormat;
import reptor.distrbt.certify.suites.JavaAuthority;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.connect.AbstractNetworkTransmissionConnectionConfiguration;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.connect.ConnectionCertifierCollection;
import reptor.replct.connect.Connections;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.map.Mapping;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyClientResults;
import reptor.tbft.TroxyHandshakeResults;
import reptor.tbft.invoke.TransBFTInvocation;


public class JavaTroxy implements Troxy, SelectorDomainContext, ChronosAddress
{

    public static final MacCertification PROPOSAL_CERTIFICATION = new PlainSingleMacFormat( Authenticating.HMAC_SHA256, null );

    private static final Key KEY = Authenticating.createKey( "SECRET" );

    // These arrays are partitioned among threads. Therefore, all handlers should be independent from each other.
    private final HandshakeConnector[]  m_hsconns;
    private final ClientHandler[]       m_clients;
    private final ProposalVerifier[]    m_propverifs;
    private final boolean               m_useCache;
    private final Cache                 m_cache;
    private final byte                  replicano;


    public JavaTroxy(byte repno, TransBFTInvocation invoke)
    {
        Connections connect = invoke.getConnections();
        Mapping     map     = invoke.getMapping();
        m_useCache          = invoke.isUseCache();
        if (m_useCache)
            m_cache         = new Cache(repno);
        else
            m_cache         = null;

        replicano = repno;

        // Handshakes
        HandshakingProcess<? extends RemoteEndpoint> hsproc = invoke.getReplicaHandshake().getBase().createAcceptorProcess( repno );

        m_hsconns = new HandshakeConnector[ invoke.getNumberOfHandshakeHandlers() ];

        for( short hsno=0; hsno<m_hsconns.length; hsno++ )
        {
            Handshake<? extends RemoteEndpoint> handshake = hsproc.createHandshake( this, (short) 0, hsno );

            m_hsconns[ hsno ] = new HandshakeConnector( this, hsno, handshake );
        }

        // Clients
        int nclients = invoke.getClientToWorkerAssignment().getNumberOfClients();
        int clinooff = invoke.getReplicaGroup().size();

        AbstractNetworkTransmissionConnectionConfiguration connconf =
                (AbstractNetworkTransmissionConnectionConfiguration) connect.createReplicaToClientConnectionConfiguration( ConnectionObserver.EMPTY );
        int clisendbufsize = connconf.getNetworkTransmitBufferSize();
        int clirecvbufsize = connconf.getNetworkReceiveBufferSize();

        m_clients = new ClientHandler[ clinooff+nclients ];

        JavaAuthority auth = new JavaAuthority();

        for( short clino=(short) clinooff; clino<nclients+clinooff; clino++ )
        {
            // Create independent certifier
            MacProvider                   propcerts  = PROPOSAL_CERTIFICATION.createCertificationProvider( auth );
            BidirectionalCertifier        propcertif = propcerts.createMessageCertifier( KEY );
            ConnectionCertifierCollection replverif = new ConnectionCertifierCollection( propcertif, invoke.getReplicaGroup().size() );

            m_clients[ clino ] = new ClientHandler( this, clino, map.createClientMessageMapper(), propcertif, replverif,
                                                    connect.useSslForClientConnections(), clisendbufsize, clirecvbufsize, repno, invoke, m_cache );
        }

        // Proposals
        m_propverifs = new ProposalVerifier[ invoke.getOrdering().getNumberOfWorkers() ];

        for( int osno=0; osno<m_propverifs.length; osno++ )
        {
            MacProvider                   propcerts  = PROPOSAL_CERTIFICATION.createCertificationProvider( auth );
            BidirectionalCertifier        propcertif = propcerts.createMessageCertifier( KEY );

            m_propverifs[ osno ] = new ProposalVerifier( map.createClientMessageMapper(), propcertif );
        }
    }


    @Override
    public String toString()
    {
        return "TROXY";
    }


    @Override
    public void initHandshake(short hsno, TroxyHandshakeResults results)
    {
        m_hsconns[ hsno ].init( results );
    }

    @Override
    public TroxyHandshakeResults resetHandshake(short hsno, boolean clear)
    {
        return m_hsconns[ hsno ].reset( clear );
    }


    @Override
    public TroxyHandshakeResults accept(short hsno, InetSocketAddress remaddr) throws IOException
    {
        return m_hsconns[ hsno ].accept( remaddr );
    }


    @Override
    public int getHandshakeInboundMinimumBufferSize(short hsno)
    {
        return m_hsconns[ hsno ].getInboundMinimumBufferSize();
    }


    @Override
    public int getHandshakeOutboundMinimumBufferSize(short hsno)
    {
        return m_hsconns[ hsno ].getOutboundMinimumBufferSize();
    }


    @Override
    public TroxyHandshakeResults processHandshakeInboundData(short hsno, ByteBuffer src) throws IOException
    {
        return m_hsconns[ hsno ].processInboundData( src );
    }


    @Override
    public TroxyHandshakeResults retrieveHandshakeOutboundData(short hsno, ByteBuffer dst) throws IOException
    {
        return m_hsconns[ hsno ].retrieveOutboundData( dst );
    }


    @Override
    public void saveState(short hsno)
    {
        TroxyHandshakeResults results = m_hsconns[ hsno ].getResults();

        assert results.isFinished();

        m_clients[ results.getRemoteEndpoint().getProcessNumber() ].installSslState( m_hsconns[ hsno ].saveSslState() );
    }


    @Override
    public void initClientHandler(short clino, TroxyClientResults results)
    {
        m_clients[ clino ].init( results );
    }


    @Override
    public TroxyClientResults open(short clino)
    {
        return m_clients[ clino ].open();
    }


    @Override
    public int getClientInboundMinimumBufferSize(short clino)
    {
        return m_clients[ clino ].getInboundMinimumBufferSize();
    }


    @Override
    public int getClientOutboundMinimumBufferSize(short clino)
    {
        return m_clients[ clino ].getOutboundMinimumBufferSize();
    }


    @Override
    public TroxyClientResults processClientInboundData(short clino, ByteBuffer src) throws IOException
    {
        return m_clients[ clino ].processInboundData( src );
    }


    @Override
    public TroxyClientResults retrieveClientOutboundData(short clino, ByteBuffer dst) throws IOException
    {
        return m_clients[ clino ].retrieveOutboundData( dst );
    }


    @Override
    public TroxyClientResults retrieveOutboundMessages(short clino)
    {
        return m_clients[ clino ].retrieveOutboundMessages();
    }


    @Override
    public TroxyClientResults handleForwardedRequest(short clino, Data request) throws VerificationException
    {
        return m_clients[ clino ].handleForwardedRequest( request );
    }


    @Override
    public TroxyClientResults handleRequestExecuted(short clino, long invno, ImmutableData result, boolean replyfull)
    {
        return m_clients[ clino ].handleRequestExecuted( invno, result, replyfull );
    }


    @Override
    public TroxyClientResults handleReply(short clino, Data reply) throws VerificationException
    {
        return m_clients[ clino ].handleReply( reply );
    }


    // TODO: If followers stored requests from clients connected to them, they could verify these requests by comparing
    //       them when the requests are received as proposals. However, with would require coordination/synchronization
    //       between proposal verifiers and client handlers.
    @Override
    public void verifyProposal(int osno, Data[] cmdbuf, int ncmds) throws VerificationException
    {
        m_propverifs[ osno ].verifyProposal( cmdbuf, ncmds, m_useCache, replicano, m_clients);
    }


    @Override
    public ChronosAddress getDomainAddress()
    {
        return this;
    }


    @Override
    public long time()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public TimeKey registerTimer(TimerHandler handler)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean checkDomain()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public ChronosDomain getCurrentDomain()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public SelectionKey registerChannel(ChannelHandler handler, SelectableChannel channel, int ops)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void prepareMigrationOfRegisteredChannel(SelectionKey key)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public SelectionKey migrateRegisteredChannel(SelectionKey key, ChannelHandler handler)
    {
        throw new UnsupportedOperationException();
    }

}
