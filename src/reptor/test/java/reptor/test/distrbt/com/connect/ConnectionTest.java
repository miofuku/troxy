package reptor.test.distrbt.com.connect;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import ch.qos.logback.classic.Level;
import reptor.chronos.domains.DomainController;
import reptor.chronos.domains.DomainThread;
import reptor.chronos.schedule.GenericScheduler;
import reptor.chronos.schedule.GenericSchedulerTask;
import reptor.chronos.schedule.ScanScheduler;
import reptor.distrbt.certify.signature.SelfCertifier;
import reptor.distrbt.certify.signature.SignatureAlgorithm;
import reptor.distrbt.certify.signature.Signing;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.distrbt.com.connect.ConnectionConfiguration;
import reptor.distrbt.com.connect.NetworkEndpointWorker;
import reptor.distrbt.com.connect.NetworkHandshakeWorker;
import reptor.distrbt.com.connect.PushNetworkTransmissionConnection;
import reptor.distrbt.com.connect.StandardConnectionConfiguration;
import reptor.distrbt.com.handshake.AdaptiveSslHandshake.SslMode;
import reptor.distrbt.com.map.BasicMessageDigestionStrategy;
import reptor.distrbt.com.map.BasicMessageMapperFactory;
import reptor.distrbt.domains.SelectorDomain;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.distrbt.io.ssl.SslConfiguration;
import reptor.jlib.hash.Hashing;
import reptor.replct.connect.AdaptiveSslHandshaking.AdaptiveSslHandshakingProcess;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.StandardHandshaking.StandardHandshakingProcess;
import reptor.test.common.Logging;
import reptor.test.distrbt.com.connect.PongHandlers.PongClient;
import reptor.test.distrbt.com.connect.PongHandlers.PongConfiguration;
import reptor.test.distrbt.com.connect.PongHandlers.PongConfigurator;
import reptor.test.distrbt.com.connect.PongHandlers.PongServer;
import reptor.test.distrbt.com.connect.PongMessages.Count;


public class ConnectionTest
{

    static
    {
        Logging.initRootLogger( Level.INFO );
//        Logging.initRootLogger( Level.ALL );
//        SslConfiguration.enableDebuggingOutput();
    }


    private static Supplier<MessageMapper> createMessageMapperFactory()
    {
        NetworkMessageRegistry msgreg = new NetworkMessageRegistry.NetworkMessageRegistryBuilder()
                                                .addMessageType( PongMessages.PONG_COUNT_ID, Count::new )
                                                .createRegistry();

        return new BasicMessageMapperFactory( msgreg, BasicMessageDigestionStrategy.Variant.Plain, Hashing.SHA256 );
    }


    static void execute(boolean singledomainmode, int netbufsize, SslMode sslmode, PongConfiguration pongconf) throws Exception
    {
        // Create domains
        GenericScheduler<SelectorDomainContext> ssched, csched;
        List<DomainThread> domains = new ArrayList<>();

        if( singledomainmode )
        {
            SelectorDomain dom = new SelectorDomain( "CONNTEST" );
            domains.add( new DomainThread( dom, null ) );

//            GenericSchedulerTask<SelectorDomainContext> sched = new SingleRoundScheduler<>( dom );
            GenericSchedulerTask<SelectorDomainContext> sched = new ScanScheduler<>();
            dom.bindTask( sched );

            ssched = csched = sched;
        }
        else
        {
            SelectorDomain sdom = new SelectorDomain( "CONNTEST-SERVER" );
            domains.add( new DomainThread( sdom, null ) );
            ssched = sdom;

            SelectorDomain cdom = new SelectorDomain( "CONNTEST-CLIENT" );
            domains.add( new DomainThread( cdom, null ) );
            csched = cdom;
        }

        DomainController domctrl = new DomainController( domains );

        // Initialize global configuration
        Supplier<MessageMapper> mapfac = createMessageMapperFactory();

        SslConfiguration ssslconf, csslconf;
        SslMode ssslmode, csslmode;
        boolean sslconn;

        if( sslmode==SslMode.DEACTIVATED )
        {
            ssslconf = csslconf = null;
            ssslmode = csslmode = sslmode;
            sslconn = false;
        }
        else
        {
            SignatureAlgorithm sigalgo   = Signing.RSA_1024_SHA256;
            KeyPairGenerator   keygen    = sigalgo.getKeyType().keyGenerator();
            SelfCertifier      certifier = new SelfCertifier( sigalgo );

            KeyPair     skeys = keygen.generateKeyPair();
            Certificate scert = certifier.certify( "server", skeys );
            KeyPair     ckeys = keygen.generateKeyPair();
            Certificate ccert = certifier.certify( "client", ckeys );

            ssslconf = new SslConfiguration( "server", skeys.getPrivate(), scert, Collections.singletonMap( "client", ccert ) );
            csslconf = new SslConfiguration( "client", ckeys.getPrivate(), ccert, Collections.singletonMap( "server", scert ) );

            ssslmode = sslmode==SslMode.CONNECT ? SslMode.ACCEPT : sslmode;
            csslmode = sslmode;
            sslconn = sslmode!=SslMode.ACCEPT;
        }

        // Create server handler
        ConnectionConfiguration sconnconf =
                new StandardConnectionConfiguration( ConnectionObserver.EMPTY, netbufsize, netbufsize, true, sslconn, false );

        MessageMapper     smapper = mapfac.get();
        SingleConnectionPeerHandler shandler = new SingleConnectionPeerHandler( ssched.getContext(), domctrl, smapper );
        HandshakingProcess<?> shsprot = new AdaptiveSslHandshakingProcess<>( (short) 0, new StandardHandshakingProcess( (short) 0 ), ssslconf, ssslmode );
        NetworkEndpointWorker   sep     = new NetworkEndpointWorker( shandler, "NEP", (short) 1, 1, shsprot::createHandlers, 100 );
        PushNetworkTransmissionConnection sconn   = sconnconf.connectionProvider( smapper ).connection( shandler.getDomainContext(), 0, null );
        sconn.bindToMaster( shandler );
        PongServer        speer   = new PongServer( shandler, pongconf );
        shandler.init( sep, sconn, speer );
        ssched.registerTask( shandler );

        // Create client handler
        ConnectionConfiguration cconnconf =
                new StandardConnectionConfiguration( ConnectionObserver.EMPTY, netbufsize, netbufsize, true, sslconn, false );

        MessageMapper     cmapper = mapfac.get();
        SingleConnectionPeerHandler chandler = new SingleConnectionPeerHandler( csched.getContext(), domctrl, cmapper );
        HandshakingProcess<?> chsprot = new AdaptiveSslHandshakingProcess<>( (short) 1, new StandardHandshakingProcess( (short) 1 ), csslconf, csslmode );
        NetworkHandshakeWorker  ccnctr  = new NetworkHandshakeWorker( chandler, "CON", (short) 2, 1, chsprot::createHandlers, 100 );
        PushNetworkTransmissionConnection cconn   = cconnconf.connectionProvider( cmapper ).connection( chandler.getDomainContext(), 1, null );
        cconn.bindToMaster( chandler );
        PongClient        cpeer   = new PongClient( chandler, pongconf );
        chandler.init( ccnctr, cconn, cpeer );
        csched.registerTask( chandler );

        // Initiate connection process
        InetSocketAddress addr = new InetSocketAddress( "localhost", 4223 );
        sep.open( addr );
        ccnctr.startConnection( chsprot.createConnectionArguments( new RemoteEndpoint( (short) 0, (short) 0 ), addr ) );

        // Execute domain(s)
        if( singledomainmode )
            domains.get( 0 ).execute();
        else
            domctrl.executeDomains();
    }


    public static void main(String[] args) throws Exception
    {
        PongConfigurator pongconf = new PongConfigurator();
        int netbufsize = 65536;

        execute( true, netbufsize, SslMode.CONNECT, pongconf );

        System.out.println( "exit." );
    }

}
