package reptor.distrbt.io.select;

import java.util.function.Consumer;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;


public class BufferedConnectionSelection implements CommunicationLayer<CommunicationSink, CommunicationSource, BufferedDataSource, BufferedDataSink>
{

    private final BufferedSourceSelector    m_inbound;
    private final BufferedSinkSelector      m_outbound;


    public BufferedConnectionSelection(Consumer<? super BufferedDataSource> chooser)
    {
        m_inbound  = new BufferedSourceSelector( chooser );
        m_outbound = new BufferedSinkSelector();
    }


    @Override
    public BufferedSourceSelector getInboundStage()
    {
        return m_inbound;
    }


    @Override
    public BufferedSinkSelector getOutboundStage()
    {
        return m_outbound;
    }


    @Override
    public BufferedSourceSelector getInbound()
    {
        return m_inbound;
    }


    @Override
    public BufferedSinkSelector getOutbound()
    {
        return m_outbound;
    }


    @Override
    public CommunicationSink getInboundConnect()
    {
        return null;
    }


    @Override
    public CommunicationSource getOutboundConnect()
    {
        return null;
    }


    public void activate()
    {
        m_inbound.activate();
        m_outbound.activate();
    }


    public void deactivate()
    {
        m_inbound.deactivate();
        m_outbound.deactivate();
    }


    public void clear()
    {
        m_inbound.clear();
        m_outbound.clear();
    }


    public boolean isActivated()
    {
        return m_inbound.isActivated() || m_outbound.isActivated();
    }


    public void select(ConnectionEndpoint<? extends BufferedDataSource, ? extends BufferedDataSink> endpoint, boolean isfixed)
    {
        m_inbound.select( endpoint.getInbound(), isfixed );
        m_outbound.select( endpoint.getOutbound() );
    }


    public void select(BufferedDataSource source, boolean isfixed, BufferedDataSink sink)
    {
        m_inbound.select( source, isfixed );
        m_outbound.select( sink );
    }

}
