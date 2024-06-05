package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

import javax.net.ssl.SSLContext;

import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.connect.BinaryUnbufferedConnector;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.select.UnbufferedConnectorSelection;
import reptor.distrbt.io.ssl.Ssl;
import reptor.distrbt.io.ssl.SslState;


public class AdaptiveSslHandshake<R> implements Handshake<R>
{

    public enum SslMode
    {
        ACTIVATED,
        CONNECT,
        ACCEPT,
        DEACTIVATED,
    }

    private final Handshake<R>                  m_next;

    private final SslMode                       m_sslmode;
    private final Ssl                           m_ssl;
    private final ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>   m_sslconn;

    private final UnbufferedConnectorSelection  m_protsel;

    private final ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>   m_conn;

    private HandshakeRole                       m_role;
    private InetSocketAddress                   m_remaddr;
    private Boolean                             m_isssl;

    private final int MINIMUM_SELECTION_BUFFER_SIZE = 1;
    private Byte                                m_selprot;

    private LogChannel                          m_logchannel;


    public AdaptiveSslHandshake(SelectorDomainContext domcntxt, Handshake<R> next, SSLContext sslcntxt, SslMode sslmode)
    {
        m_next = Objects.requireNonNull( next );

        m_sslmode = Objects.requireNonNull( sslmode );

        if( sslmode==SslMode.DEACTIVATED )
        {
            m_ssl        = null;
            m_sslconn    = null;
            m_protsel    = null;
            m_conn       = next;
        }
        else if( sslcntxt==null )
        {
            throw new IllegalArgumentException();
        }
        else
        {
            m_ssl     = new Ssl( domcntxt, sslcntxt );
            m_ssl.adjustBuffer( next );
            m_sslconn = new BinaryUnbufferedConnector( m_ssl, next );

            if( sslmode==SslMode.ACTIVATED )
            {
                m_protsel = null;
                m_conn = m_sslconn;
            }
            else
            {
                int minnextbufsize = Math.max( next.getInboundConnect().getMinimumBufferSize(), next.getOutboundConnect().getMinimumBufferSize() );
                m_protsel = new UnbufferedConnectorSelection( this::configureStack, Math.max( minnextbufsize, MINIMUM_SELECTION_BUFFER_SIZE ) );
                m_conn = m_protsel;
            }
        }
    }


    @Override
    public UnbufferedDataSink getInboundConnect()
    {
        return m_conn.getInboundConnect();
    }


    @Override
    public UnbufferedDataSource getOutboundConnect()
    {
        return m_conn.getOutboundConnect();
    }


    @Override
    public void initLogChannel(LogChannel logchannel)
    {
        m_logchannel = logchannel;
        m_next.initLogChannel( logchannel );
    }


    @Override
    public void activate()
    {
        if( m_isssl==null )
            enableSelector();
        else
        {
            if( m_protsel!=null )
                m_protsel.activate();

            if( m_isssl )
                m_ssl.activate();

            m_next.activate();
        }
    }


    @Override
    public void deactivate()
    {
        if( m_isssl==null )
            disableSelector();
        else
        {
            if( m_protsel!=null )
                m_protsel.activate();

            if( m_isssl )
                m_ssl.deactivate();

            m_next.deactivate();
        }
    }


    @Override
    public boolean isActivated()
    {
        return m_next.isActivated() || m_ssl!=null && m_ssl.isActivated() || m_protsel!=null && m_protsel.isActivated();
    }


    @Override
    public void reset(boolean clear)
    {
        m_next.reset( clear );

        if( m_ssl!=null )
            m_ssl.clear();

        // Clear the selector last since it will try to unbind the selected stage.
        if( m_protsel!=null )
            m_protsel.clear();

        m_isssl   = null;
        m_selprot = null;

        if( clear )
        {
            m_role    = null;
            m_remaddr = null;
        }
    }


    @Override
    public void initConnection(Object args)
    {
        m_next.initConnection( args );
    }


    @Override
    public void connect(InetSocketAddress remaddr) throws IOException
    {
        m_role    = HandshakeRole.CONNECTOR;
        m_remaddr = remaddr;

        switch( m_sslmode )
        {
        case DEACTIVATED:
            configureStandard();
            break;
        case ACTIVATED:
            configureSsl();
            break;
        case CONNECT:
            selectSsl();
            break;
        case ACCEPT:
            selectStandard();
            break;
        }

        m_next.connect( remaddr );
    }


    @Override
    public void accept(InetSocketAddress remaddr) throws IOException
    {
        m_role    = HandshakeRole.ACCEPTOR;
        m_remaddr = remaddr;

        switch( m_sslmode )
        {
        case DEACTIVATED:
            configureStandard();
            break;
        case ACTIVATED:
            configureSsl();
            break;
        case CONNECT:
        case ACCEPT:
            configureSelector();
            break;
        }

        m_next.accept( remaddr );
    }


    @Override
    public boolean needsReconfiguraiton()
    {
        return m_selprot!=null;
    }


    @Override
    public void reconfigure()
    {
        switch( m_selprot )
        {
        case HandshakeMagic.STD:
            selectStandard();
            break;

        case HandshakeMagic.SSL:
            selectSsl();
            break;

        default:
            throw new UnsupportedOperationException( String.valueOf( m_protsel ) );
        }

        m_protsel.activate();

        if( m_isssl )
            m_ssl.activate();

        m_next.activate();

        m_selprot = null;
        m_protsel.getInboundConnect().signal();
    }


    @Override
    public R getRemote()
    {
        return m_next.getRemote();
    }


    @Override
    public boolean isFinished()
    {
        return m_next.isFinished();
    }


    @Override
    public void saveState(BufferedNetworkState netstate, SslState sslstate)
    {
        m_next.saveState( netstate, m_isssl==Boolean.TRUE ? m_ssl.initiateMigration() : null );
    }


    @Override
    public HandshakeState getState()
    {
        return m_next.getState();
    }


    @Override
    public String getConnectionDescription()
    {
        String ssls;

        if( m_isssl==null )
            ssls = "?";
        else if( m_isssl==Boolean.TRUE )
            ssls = "ssl";
        else
            ssls = "std";

        return String.format( "%s (%s)", m_next.getConnectionDescription(), ssls );
    }


    private void configureStandard()
    {
        m_isssl = false;

        log( "configure", ": standard", null );
    }


    private void configureSsl()
    {
        m_isssl = true;

        log( "configure", ": SSL", null );

        m_ssl.init( m_remaddr, m_role==HandshakeRole.CONNECTOR );
    }


    private void configureSelector()
    {
        log( "configure", ": selector", null );
    }


    private void enableSelector()
    {
        m_protsel.getInboundConnect().activate();
    }


    private void disableSelector()
    {
        m_protsel.getInboundConnect().deactivate();
    }


    private void selectStandard()
    {
        m_isssl = false;

        log( "configure", ": standard (selected)", null );

        m_protsel.select( m_next );
    }


    private void selectSsl()
    {
        m_isssl = true;

        log( "configure", ": SSL (selected)", null );

        m_protsel.select( m_sslconn );

        // Requires an initialized master to obtain channel information.
        m_ssl.init( m_remaddr, m_role==HandshakeRole.CONNECTOR );
    }


    // Invoked by the inbound protocol selector.
    private void configureStack(ByteBuffer data)
    {
        m_selprot = data.get( data.position() );

        disableSelector();
    }


    protected void log(String action, String msg, Object arg)
    {
        if( m_logchannel!=null )
            m_logchannel.log( action, msg, arg );
    }

}
