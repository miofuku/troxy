package reptor.distrbt.io.connect;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.channel.BinaryBufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryBufferedSourceChannel;


public class BinaryBufferedConnector implements ConnectorEndpoint<BufferedDataSink, BufferedDataSource>
{

    private final BinaryBufferedSinkChannel     m_inbound;
    private final BinaryBufferedSourceChannel   m_outbound;


    public BinaryBufferedConnector(
            CommunicationLayer<? extends BufferedDataSink, ? extends BufferedDataSource,
                               ? extends UnbufferedDataSource, ? extends UnbufferedDataSink> previous,
            ConnectorEndpoint<? extends BufferedDataSink, ? extends BufferedDataSource> endpoint)
    {
        m_inbound  = new BinaryBufferedSinkChannel( previous.getInboundStage(), endpoint.getInboundConnect() );
        m_outbound = new BinaryBufferedSourceChannel( endpoint.getOutboundConnect(), previous.getOutboundStage() );
    }


    @Override
    public BufferedDataSink getInboundConnect()
    {
        return m_inbound;
    }


    @Override
    public BufferedDataSource getOutboundConnect()
    {
        return m_outbound;
    }

}
