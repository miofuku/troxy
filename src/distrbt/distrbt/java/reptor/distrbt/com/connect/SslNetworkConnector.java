package reptor.distrbt.com.connect;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.ConnectionEndpoint;
import reptor.chronos.com.PushMessageSource;
import reptor.distrbt.com.NetworkConnection;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkTransmissionLayer;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.map.NetworkMessageSink;
import reptor.distrbt.com.map.PushMessageEncoding;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.net.BufferedNetwork;
import reptor.distrbt.io.ssl.Ssl;
import reptor.distrbt.io.stage.LayerStage;


public class SslNetworkConnector
        implements NetworkTransmissionLayer<UnbufferedDataSink, UnbufferedDataSource, PushMessageSource<NetworkMessage>, NetworkMessageSink>,
                   ConnectionEndpoint<PushMessageSource<NetworkMessage>, NetworkMessageSink>
{

    private static final Logger s_logger = LoggerFactory.getLogger( SslNetworkConnector.class );

    private final Ssl                               m_ssl;
    private final PushMessageEncoding               m_msgenc;

    private final CommunicationStage<? extends UnbufferedDataSink, ? extends PushMessageSource<NetworkMessage>>   m_instage;
    private final CommunicationStage<? extends NetworkMessageSink, ? extends UnbufferedDataSource>                m_outstage;


    public SslNetworkConnector(BufferedNetwork net, Ssl ssl, PushMessageEncoding msgenc)
    {
        m_ssl    = Objects.requireNonNull( ssl );
        m_msgenc = Objects.requireNonNull( msgenc );

        m_instage  = new LayerStage<>( this, m_ssl.getInboundStage().getSink(), m_msgenc.getInboundStage().getSource() );
        m_outstage = new LayerStage<>( this, m_msgenc.getOutboundStage().getSink(), m_ssl.getOutboundStage().getSource() );
    }


    @Override
    public void open(NetworkConnection<?, ?> conn, HandshakeState hsstate) throws IOException
    {
        SocketChannel channel = hsstate.getBufferedNetworkState().getNetworkState().getChannel();

        s_logger.debug( "{} open ssl with {}", conn, channel );

        m_ssl.installState( hsstate.getSslState() );
    }


    @Override
    public CommunicationStage<? extends UnbufferedDataSink, ? extends PushMessageSource<NetworkMessage>> getInboundStage()
    {
        return m_instage;
    }


    @Override
    public CommunicationStage<? extends NetworkMessageSink, ? extends UnbufferedDataSource> getOutboundStage()
    {
        return m_outstage;
    }


    @Override
    public UnbufferedDataSink getInboundConnect()
    {
        return m_ssl.getInboundConnect();
    }


    @Override
    public UnbufferedDataSource getOutboundConnect()
    {
        return m_ssl.getOutboundConnect();
    }


    @Override
    public NetworkMessageSink getOutbound()
    {
        return m_msgenc.getOutbound();
    }


    @Override
    public PushMessageSource<NetworkMessage> getInbound()
    {
        return m_msgenc.getInbound();
    }


    @Override
    public void activate()
    {
        m_ssl.activate();
        m_msgenc.activate();
    }


    @Override
    public void deactivate()
    {
        m_ssl.deactivate();
        m_msgenc.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_ssl.isActivated() || m_msgenc.isActivated();
    }

}