package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

import javax.net.ssl.SSLContext;

import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.connect.BinaryUnbufferedConnector;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.Ssl;
import reptor.distrbt.io.ssl.SslState;


public class SslHandshake<R> implements Handshake<R>
{

    private final Handshake<R>      m_next;
    private final Ssl               m_ssl;

    private final ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>   m_conn;


    public SslHandshake(SelectorDomainContext domcntxt, Handshake<R> next, SSLContext sslcntxt)
    {
        m_next = Objects.requireNonNull( next );

        m_ssl = new Ssl( domcntxt, sslcntxt );
        m_ssl.adjustBuffer( m_next );

        m_conn = new BinaryUnbufferedConnector( m_ssl, m_next );
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
    public void initLogChannel(reptor.distrbt.com.handshake.Handshake.LogChannel logchannel)
    {
        m_next.initLogChannel( logchannel );
    }


    @Override
    public void activate()
    {
        m_ssl.activate();
        m_next.activate();
    }


    @Override
    public void deactivate()
    {
        m_next.deactivate();
        m_ssl.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_next.isActivated() || m_ssl.isActivated();
    }


    @Override
    public void reset(boolean clear)
    {
        m_next.reset( clear );
        m_ssl.clear();
    }


    @Override
    public void initConnection(Object args)
    {
        m_next.initConnection( args );
    }


    @Override
    public void connect(InetSocketAddress remaddr) throws IOException
    {
        m_ssl.init( remaddr, true );
        m_next.connect( remaddr );
    }


    @Override
    public void accept(InetSocketAddress remaddr) throws IOException
    {
        m_ssl.init( remaddr, false );
        m_next.accept( remaddr );
    }


    @Override
    public boolean needsReconfiguraiton()
    {
        return m_next.needsReconfiguraiton();
    }


    @Override
    public void reconfigure()
    {
        m_next.reconfigure();
        m_ssl.adjustBuffer( m_next );
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
        m_next.saveState( netstate, m_ssl.initiateMigration() );
    }


    @Override
    public HandshakeState getState()
    {
        return m_next.getState();
    }


    @Override
    public String getConnectionDescription()
    {
        return String.format( "%s (ssl)", m_next.getConnectionDescription() );
    }

}
