package reptor.tbft.jtroxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.ssl.SslState;
import reptor.replct.connect.RemoteEndpoint;
import reptor.tbft.TroxyHandshakeResults;


class HandshakeConnector extends NetworkConnector
{

    private static final Logger s_logger = LoggerFactory.getLogger( HandshakeConnector.class );


    private final short                                 m_hsno;
    private final Handshake<? extends RemoteEndpoint>   m_handshake;

    private InetSocketAddress               m_remaddr;

    private TroxyHandshakeResults           m_results;


    public HandshakeConnector(SelectorDomainContext domcntxt, short handlno,
                              Handshake<? extends RemoteEndpoint> handshake)
    {
        m_hsno      = handlno;
        m_handshake = Objects.requireNonNull( handshake );

        initEndpoint( domcntxt, m_handshake );

        if( s_logger.isDebugEnabled() )
            m_handshake.initLogChannel( this::log );
    }


    @Override
    public String toString()
    {
        return String.format( "JTROXYHS[%02d]", m_hsno );
    }


    public TroxyHandshakeResults getResults()
    {
        return m_results;
    }


    public SslState saveSslState()
    {
        m_handshake.saveState( null, null );

        return m_handshake.getState().getSslState();
    }


    public void init(TroxyHandshakeResults results)
    {
        m_results = results;

        initNetworkResults( results );
    }


    public TroxyHandshakeResults reset(boolean clear)
    {
        if( clear )
            m_remaddr = null;

        m_handshake.reset( clear );

        return updateStatus();
    }


    public TroxyHandshakeResults accept(InetSocketAddress remaddr) throws IOException
    {
        m_remaddr = remaddr;

        m_handshake.accept( remaddr );
        m_handshake.activate();

        return updateStatus();
    }


    public int getInboundMinimumBufferSize()
    {
        return m_handshake.getInboundConnect().getMinimumBufferSize();
    }


    public int getOutboundMinimumBufferSize()
    {
        return m_handshake.getOutboundConnect().getMinimumBufferSize();
    }


    public TroxyHandshakeResults processInboundData(ByteBuffer src) throws IOException
    {
        m_inbound.processData( src );

        return updateStatus();
    }


    // TODO: Directly via results?
    public TroxyHandshakeResults retrieveOutboundData(ByteBuffer dst) throws IOException
    {
        m_outbound.retrieveData( dst );

        return updateStatus();
    }


    private TroxyHandshakeResults updateStatus()
    {
        m_results.setRemoteEndpoint( m_handshake.getRemote() );
        m_results.isFinished( m_handshake.isFinished() );

        return m_results;
    }


    protected void log(String action, String msg, Object arg)
    {
        if( arg instanceof Exception )
            arg = arg.toString();

        s_logger.debug( "{} {} {} {}" + msg, toString(), action, m_handshake.getConnectionDescription(), m_remaddr, arg );
    }

}
