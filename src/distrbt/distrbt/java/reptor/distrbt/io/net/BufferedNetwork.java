package reptor.distrbt.io.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.IntFunction;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationLayerElement;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.UnbufferedEndpoint;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;


public class BufferedNetwork implements ConnectionEndpoint<BufferedDataSource, BufferedDataSink>, CommunicationLayerElement

{

    private final Network           m_net;
    private final NetworkBuffering  m_netbuf;

    public BufferedNetwork(SelectorDomainContext domcntxt, ConnectionObserver observer,
                           ConnectorEndpoint<? extends UnbufferedEndpoint, ? extends UnbufferedEndpoint> conn,
                           IntFunction<ByteBuffer> buffac)
    {
        this( domcntxt, observer, conn.getInboundConnect().getMinimumBufferSize(), conn.getOutboundConnect().getMinimumBufferSize(), buffac );
    }


    public BufferedNetwork(SelectorDomainContext domcntxt, ConnectionObserver observer,
                           int recvbufsize, int sendbufsize, IntFunction<ByteBuffer> buffac)
    {
        m_net    = new Network( domcntxt, observer );
        m_netbuf = new NetworkBuffering( m_net, recvbufsize, sendbufsize, buffac );
    }


    public Network getNetwork()
    {
        return m_net;
    }


    public NetworkBuffering getNetworkBuffering()
    {
        return m_netbuf;
    }


    @Override
    public NetworkSourceBuffer getInbound()
    {
        return m_netbuf.getInbound();
    }


    @Override
    public NetworkSinkBuffer getOutbound()
    {
        return m_netbuf.getOutbound();
    }


    public void init(SocketChannel channel, SelectionKey selkey) throws IOException
    {
        m_net.init( channel, selkey );
    }


    @Notifying
    public void open(SocketChannel channel, SelectionKey selkey) throws IOException
    {
        m_net.open( channel, selkey );
        m_netbuf.activate();
    }


    public void close()
    {
        m_net.close();
    }


    public boolean isOpen()
    {
        return m_net.isOpen();
    }


    public void clear(boolean close)
    {
        m_net.clear( close );
        m_netbuf.clear();
    }


    public boolean isInitialized()
    {
        return m_net.isInitialized();
    }


    @Override
    public void activate()
    {
        m_net.activate();
        m_netbuf.activate();
    }


    @Override
    public void deactivate()
    {
        m_net.deactivate();
        m_netbuf.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_net.isActivated() || m_netbuf.isActivated();
    }


    public BufferedNetworkState initiateMigration()
    {
        return new BufferedNetworkState( m_net.initiateMigration(), m_netbuf.initiateMigration() );
    }


    public void installState(BufferedNetworkState state) throws IOException
    {
        installState( state.getNetworkState(), state.getNetworkBufferingState() );
    }


    public void installState(NetworkState netstate, NetworkBufferingState netbufstate) throws IOException
    {
        m_net.installState( netstate );
        m_netbuf.installState( netbufstate );
    }


    public void adjustBuffer(ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> conn)
    {
        m_netbuf.adjustBuffer( conn );
    }


    public void adjustBuffer(int mininbufsize, int minoutbufsize)
    {
        m_netbuf.adjustBuffer( mininbufsize, minoutbufsize );
    }

}
