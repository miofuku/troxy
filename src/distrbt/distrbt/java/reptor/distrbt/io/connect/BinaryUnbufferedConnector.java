package reptor.distrbt.io.connect;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.channel.BinaryUnbufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryUnbufferedSourceChannel;


public class BinaryUnbufferedConnector implements ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>
{

    private final BinaryUnbufferedSinkChannel     m_inbound;
    private final BinaryUnbufferedSourceChannel   m_outbound;


    public BinaryUnbufferedConnector(
            CommunicationLayer<? extends UnbufferedDataSink, ? extends UnbufferedDataSource,
                               ? extends BufferedDataSource, ? extends BufferedDataSink> previous,
            ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> endpoint)
    {
        m_inbound  = new BinaryUnbufferedSinkChannel( previous.getInboundStage(), endpoint.getInboundConnect() );
        m_outbound = new BinaryUnbufferedSourceChannel( endpoint.getOutboundConnect(), previous.getOutboundStage() );
    }


    @Override
    public UnbufferedDataSink getInboundConnect()
    {
        return m_inbound;
    }


    @Override
    public UnbufferedDataSource getOutboundConnect()
    {
        return m_outbound;
    }

}
