package reptor.distrbt.com.map;

import java.io.IOException;
import java.util.function.IntFunction;

import reptor.chronos.com.PushMessageSource;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkConnection;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkTransmissionLayer;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.io.AdaptiveDataSource;
import reptor.distrbt.io.UnbufferedDataSink;


public class PushMessageEncoding
        implements NetworkTransmissionLayer<UnbufferedDataSink, AdaptiveDataSource, PushMessageSource<NetworkMessage>, NetworkMessageSink>
{

    private final PushMessageDecoder        m_decoder;
    private final AdaptiveMessageEncoder    m_encoder;


    public PushMessageEncoding(MessageMapper mapper, Integer srcid, IntFunction<Object> msgcntxtfac)
    {
        m_decoder = new PushMessageDecoder( mapper, mapper.createSourceContext( srcid, msgcntxtfac ) );
        m_encoder = new AdaptiveMessageEncoder( mapper );
    }


    @Override
    public PushMessageDecoder getInboundStage()
    {
        return m_decoder;
    }


    @Override
    public AdaptiveMessageEncoder getOutboundStage()
    {
        return m_encoder;
    }


    @Override
    public PushMessageDecoder getInboundConnect()
    {
        return m_decoder;
    }


    @Override
    public PushMessageDecoder getInbound()
    {
        return m_decoder;
    }


    @Override
    public AdaptiveMessageEncoder getOutboundConnect()
    {
        return m_encoder;
    }


    @Override
    public AdaptiveMessageEncoder getOutbound()
    {
        return m_encoder;
    }


    @Override
    public void activate()
    {
        m_decoder.activate();
        m_encoder.activate();
    }


    @Override
    public void deactivate()
    {
        m_decoder.deactivate();
        m_encoder.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_decoder.isActivated() || m_encoder.isActivated();
    }


    @Override
    public void open(NetworkConnection<?, ?> conn, HandshakeState hsstate) throws IOException
    {
    }

}
