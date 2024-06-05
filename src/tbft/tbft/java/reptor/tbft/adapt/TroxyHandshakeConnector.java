package reptor.tbft.adapt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.StandardHandshakeState;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyHandshakeResults;
import reptor.tbft.TroxyNetworkResults;


public class TroxyHandshakeConnector extends TroxyNetworkConnector implements Handshake<RemoteEndpoint>
{

    private final short                 m_locno;
    private final short                 m_hsno;

    private final TroxyHandshakeResults m_results = new TroxyHandshakeResults();
    private StandardHandshakeState      m_hsstate;


    public TroxyHandshakeConnector(Troxy troxy, short locno, short hsno)
    {
        super( troxy );

        m_hsno  = hsno;
        m_locno = locno;

        m_troxy.initHandshake( hsno, m_results );
    }


    @Override
    public String toString()
    {
        return String.format( "TROXYHS[%02d]", m_hsno );
    }


    @Override
    public void initLogChannel(LogChannel logchannel)
    {
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
    public void reset(boolean clear)
    {
        m_troxy.resetHandshake( m_hsno, clear );

        processNetworkResults( m_results );

        m_hsstate = null;
    }


    @Override
    public void initConnection(Object args)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void connect(InetSocketAddress remaddr) throws IOException
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void accept(InetSocketAddress remaddr) throws IOException
    {
        m_troxy.accept( m_hsno, remaddr );

        processNetworkResults( m_results );
    }


    @Override
    public boolean needsReconfiguraiton()
    {
        return false;
    }


    @Override
    public void reconfigure()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public int getMinimumSinkBufferSize()
    {
        return m_troxy.getHandshakeInboundMinimumBufferSize( m_hsno );
    }


    @Override
    public int getMinimumSourceBufferSize()
    {
        return m_troxy.getHandshakeOutboundMinimumBufferSize( m_hsno );
    }


    @Override
    public String getConnectionDescription()
    {
        RemoteEndpoint remep = m_results.getRemoteEndpoint();
        short remno = remep==null ? -1 : remep.getProcessNumber();
        short netno = remep==null ? -1 : remep.getNetworkNumber();

        return String.format( "%d <- %d (%d)", remno, m_locno, netno );
    }


    @Override
    public RemoteEndpoint getRemote()
    {
        return m_results.getRemoteEndpoint();
    }


    @Override
    public boolean isFinished()
    {
        return m_results.isFinished();
    }


    @Override
    public void saveState(BufferedNetworkState netstate, SslState sslstate)
    {
        assert m_hsstate==null;

        m_hsstate = new StandardHandshakeState( m_results.getRemoteEndpoint(), false, netstate, null );

        m_troxy.saveState( m_hsno );
    }


    @Override
    public HandshakeState getState()
    {
        return m_hsstate;
    }


    @Override
    protected TroxyNetworkResults processInboundData(ByteBuffer src) throws IOException
    {
        m_troxy.processHandshakeInboundData( m_hsno, src );

        return m_results;
    }


    @Override
    protected TroxyNetworkResults retrieveOutboundData(ByteBuffer dst) throws IOException
    {
        m_troxy.retrieveHandshakeOutboundData( m_hsno, dst );

        return m_results;
    }

}
