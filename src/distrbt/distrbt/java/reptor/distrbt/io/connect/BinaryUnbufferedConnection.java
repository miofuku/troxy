package reptor.distrbt.io.connect;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.channel.BinaryUnbufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryUnbufferedSourceChannel;


public class BinaryUnbufferedConnection implements ConnectionEndpoint<UnbufferedDataSource, UnbufferedDataSink>
{

    private final BinaryUnbufferedSourceChannel   m_inbound;
    private final BinaryUnbufferedSinkChannel     m_outbound;


    public BinaryUnbufferedConnection(
            ConnectionEndpoint<? extends UnbufferedDataSource, ? extends UnbufferedDataSink> endpoint,
            CommunicationLayer<? extends BufferedDataSink, ? extends BufferedDataSource,
                               ? extends UnbufferedDataSource, ? extends UnbufferedDataSink> next)
    {
        m_inbound  = new BinaryUnbufferedSourceChannel( endpoint.getInbound(), next.getInboundStage() );
        m_outbound = new BinaryUnbufferedSinkChannel( next.getOutboundStage(), endpoint.getOutbound() );
    }


    @Override
    public UnbufferedDataSource getInbound()
    {
        return m_inbound;
    }


    @Override
    public UnbufferedDataSink getOutbound()
    {
        return m_outbound;
    }

}
