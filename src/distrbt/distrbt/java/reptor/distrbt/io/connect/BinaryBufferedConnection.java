package reptor.distrbt.io.connect;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.channel.BinaryBufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryBufferedSourceChannel;


public class BinaryBufferedConnection implements ConnectionEndpoint<BufferedDataSource, BufferedDataSink>
{

    private final BinaryBufferedSourceChannel   m_inbound;
    private final BinaryBufferedSinkChannel     m_outbound;


    public BinaryBufferedConnection(
            ConnectionEndpoint<? extends BufferedDataSource, ? extends BufferedDataSink> endpoint,
            CommunicationLayer<? extends UnbufferedDataSink, ? extends UnbufferedDataSource,
                               ? extends BufferedDataSource, ? extends BufferedDataSink> next)
    {
        m_inbound  = new BinaryBufferedSourceChannel( endpoint.getInbound(), next.getInboundStage() );
        m_outbound = new BinaryBufferedSinkChannel( next.getOutboundStage(), endpoint.getOutbound() );
    }


    @Override
    public BufferedDataSource getInbound()
    {
        return m_inbound;
    }


    @Override
    public BufferedDataSink getOutbound()
    {
        return m_outbound;
    }

}
