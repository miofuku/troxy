package reptor.distrbt.io.select;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;


public class UnbufferedConnectorSelection implements CommunicationLayer<UnbufferedDataSink, UnbufferedDataSource, CommunicationSource, CommunicationSink>
{

    private final UnbufferedSinkSelector    m_inbound;
    private final UnbufferedSourceSelector  m_outbound;


    public UnbufferedConnectorSelection(Consumer<? super ByteBuffer> chooser, int minbufsize)
    {
        m_inbound  = new UnbufferedSinkSelector( chooser, minbufsize );
        m_outbound = new UnbufferedSourceSelector();
    }


    @Override
    public UnbufferedSinkSelector getInboundStage()
    {
        return m_inbound;
    }


    @Override
    public UnbufferedSourceSelector getOutboundStage()
    {
        return m_outbound;
    }


    @Override
    public CommunicationSource getInbound()
    {
        return null;
    }


    @Override
    public CommunicationSink getOutbound()
    {
        return null;
    }


    @Override
    public UnbufferedSinkSelector getInboundConnect()
    {
        return m_inbound;
    }


    @Override
    public UnbufferedSourceSelector getOutboundConnect()
    {
        return m_outbound;
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


    public void select(ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> endpoint)
    {
        m_inbound.select( endpoint.getInboundConnect() );
        m_outbound.select( endpoint.getOutboundConnect() );
    }


    public void select(UnbufferedDataSink sink, boolean isfixed, UnbufferedDataSource source)
    {
        m_inbound.select( sink );
        m_outbound.select( source );
    }

}
