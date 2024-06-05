package reptor.distrbt.io.net;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import reptor.chronos.com.CommunicationLayerElement;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.UnbufferedEndpoint;


public class NetworkBuffering implements ConnectionEndpoint<BufferedDataSource, BufferedDataSink>, CommunicationLayerElement

{

    private final NetworkSourceBuffer       m_inbound;
    private final NetworkSinkBuffer         m_outbound;


    public NetworkBuffering(Network net, ConnectorEndpoint<? extends UnbufferedEndpoint, ? extends UnbufferedEndpoint> conn,
                            IntFunction<ByteBuffer> buffac)
    {
        this( net, conn.getInboundConnect().getMinimumBufferSize(), conn.getOutboundConnect().getMinimumBufferSize(), buffac );
    }


    public NetworkBuffering(Network net, int recvbufsize, int sendbufsize, IntFunction<ByteBuffer> buffac)
    {
        m_inbound  = recvbufsize<0 ? null : new NetworkSourceBuffer( net.getInbound(), buffac, recvbufsize );
        m_outbound = sendbufsize<0 ? null : new NetworkSinkBuffer( net.getOutbound(), buffac, sendbufsize );
    }


    public NetworkBuffering(NetworkSourceBuffer inbound, NetworkSinkBuffer outbound)
    {
        m_inbound  = inbound;
        m_outbound = outbound;
    }


    @Override
    public NetworkSourceBuffer getInbound()
    {
        return m_inbound;
    }


    @Override
    public NetworkSinkBuffer getOutbound()
    {
        return m_outbound;
    }


    @Override
    public void activate()
    {
        if( m_inbound!=null )
            m_inbound.activate();
        if( m_outbound!=null )
            m_outbound.activate();
    }


    @Override
    public void deactivate()
    {
        if( m_outbound!=null )
            m_outbound.deactivate();
        if( m_inbound!=null )
            m_inbound.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_inbound!=null && m_inbound.isActivated() || m_outbound!=null && m_outbound.isActivated();
    }


    public void clear()
    {
        if( m_inbound!=null )
            m_inbound.clear();
        if( m_outbound!=null )
            m_outbound.clear();
    }


    public NetworkBufferingState initiateMigration()
    {
        ByteBuffer sourcestate = m_inbound==null ? null : m_inbound.saveState();
        ByteBuffer sinkstate   = m_outbound==null ? null : m_outbound.saveState();

        clear();

        return new NetworkBufferingState( sourcestate, sinkstate );
    }


    public void installState(NetworkBufferingState state)
    {
        installState( m_inbound, state.getSourceState() );
        installState( m_outbound, state.getSinkState() );
    }


    private void installState(NetworkEndpointBuffer netbuf, ByteBuffer state)
    {
        if( state==null || state.remaining()==0 )
            return;
        else if( netbuf==null )
            throw new UnsupportedOperationException();

        netbuf.installState( state );
    }


    public void adjustBuffer(ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> conn)
    {
        m_inbound.adjustBuffer( conn.getInboundConnect() );
        m_outbound.adjustBuffer( conn.getOutboundConnect() );
    }


    public void adjustBuffer(int mininbufsize, int minoutbufsize)
    {
        m_inbound.adjustBuffer( mininbufsize );
        m_outbound.adjustBuffer( minoutbufsize );
    }

}
