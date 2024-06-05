package reptor.distrbt.io.ssl;

import java.nio.ByteBuffer;
import java.util.Objects;

import javax.net.ssl.SSLEngine;

public class SslState
{

    private final SSLEngine     m_sslengine;
    private final ByteBuffer    m_outstate;
    private final ByteBuffer    m_instate;


    public SslState(SSLEngine sslengine, ByteBuffer outstate, ByteBuffer instate)
    {
        m_sslengine = Objects.requireNonNull( sslengine );
        m_outstate  = outstate;
        m_instate   = instate;
    }


    public SSLEngine getEngine()
    {
        return m_sslengine;
    }


    public ByteBuffer getInboundState()
    {
        return m_outstate;
    }


    public ByteBuffer getOutboundState()
    {
        return m_instate;
    }

}
