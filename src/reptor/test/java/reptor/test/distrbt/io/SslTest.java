package reptor.test.distrbt.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import ch.qos.logback.classic.Level;
import reptor.chronos.Immutable;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.schedule.SingleRoundScheduler;
import reptor.distrbt.com.connect.NetworkEndpointWorker;
import reptor.distrbt.com.connect.NetworkHandshakeWorker;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.domains.SelectorDomain;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
import reptor.jlib.collect.Slots;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.StandardConnectionArguments;
import reptor.test.common.Logging;


public class SslTest
{

    static
    {
        Logging.initRootLogger( Level.ALL );
    }


    @Immutable
    private static class SslHandshakeProtocol
    {

        private final short m_locno;


        public SslHandshakeProtocol(short locno)
        {
            m_locno = locno;
        }


        public Slots<? extends HandshakeHandler>
                createHandlers(SchedulerContext<? extends SelectorDomainContext> cntxt, short epno, int nhandlers)
        {
            throw new NotImplementedException();
        }


        public Object createConnectionArguments(short remno, InetSocketAddress addr, short netno)
        {
            return new StandardConnectionArguments( m_locno, new RemoteEndpoint( remno, netno ), addr );
        }

    }

    protected static void testSslDomain() throws Exception
    {
        SelectorDomain dom = new SelectorDomain( "SSL" );

        SingleRoundScheduler<SelectorDomainContext> sched = new SingleRoundScheduler<>();
        dom.bindTask( sched );

        SslHandshakeProtocol hsprote = new SslHandshakeProtocol( (short) 0 );
        NetworkEndpointWorker ep = new NetworkEndpointWorker( sched, "NEP", (short) 1, 1, hsprote::createHandlers, 100 );
        sched.registerTask( ep );

        SslHandshakeProtocol hsprotc = new SslHandshakeProtocol( (short) 1 );
        NetworkHandshakeWorker con = new NetworkHandshakeWorker( sched, "CON", (short) 2, 1, hsprotc::createHandlers, 100 );
        sched.registerTask( con );

        InetSocketAddress addr = new InetSocketAddress( "localhost", 4223 );
        ep.open( addr );
        con.startConnection( hsprotc.createConnectionArguments( (short) 0, addr, (short) 0 ) );

        dom.initContext();
        dom.run();
    }


    protected static void testSslSocket() throws Exception
    {
        System.setProperty( "javax.net.debug", "ssl,handshake,all" );
        SSLContext sslcntxt = SSLContext.getInstance( "TLSv1.2" );

        KeyStore keys = KeyStore.getInstance( KeyStore.getDefaultType() );

        try( FileInputStream fis = new FileInputStream( "/Users/Jo/Projects/BFTNG/projects/natfit/keystore" ) )
        {
            keys.load( fis, "password".toCharArray() );
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
        kmf.init( keys, "password".toCharArray() );

        TrustManagerFactory tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
//        KeyStore trusted = KeyStore.getInstance( KeyStore.getDefaultType() );
//        tmf.init( trusted );
        tmf.init( keys );

        sslcntxt.init( kmf.getKeyManagers(), tmf.getTrustManagers(), null );
//        sslcntxt.init( kmf.getKeyManagers(), trustAllCerts, null );
//        m_ssl = sslcntxt.createSSLEngine( "localhost", 4242 );
//        m_ssl.setEnabledCipherSuites( new String[] { "TLS_RSA_WITH_AES_128_CBC_SHA256" } );

        System.out.println( "--- Create sockets" );
        InetSocketAddress addr = new InetSocketAddress( "localhost", 2323 );
        SSLServerSocket ssocket = (SSLServerSocket) sslcntxt.getServerSocketFactory().createServerSocket();
        ssocket.setEnabledCipherSuites( new String[] { "TLS_RSA_WITH_AES_128_CBC_SHA256" } );
        ssocket.bind( addr );
        SSLSocket socket = (SSLSocket) sslcntxt.getSocketFactory().createSocket();
        socket.setEnabledCipherSuites( new String[] { "TLS_RSA_WITH_AES_128_CBC_SHA256" } );

        new Thread( new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        System.out.println( "--- Accept" );
                        SSLSocket asocket = (SSLSocket) ssocket.accept();
                        int in = asocket.getInputStream().read();
                        System.out.println();
                        System.out.println( "--- Received " + in );
                    }
                    catch( IOException e )
                    {
                        throw new IllegalStateException( e );
                    }
                }
            }).start();

        Thread.sleep( 1000 );

        System.out.println( "--- Connect" );
        socket.connect( addr );

        System.out.println();
        System.out.println( "--- Write" );
        socket.getOutputStream().write( 42 );
    }


    public static void main(String[] args) throws Exception
    {
        testSslDomain();
//        testSslSocket();

        System.out.println( "exit." );
    }

}
