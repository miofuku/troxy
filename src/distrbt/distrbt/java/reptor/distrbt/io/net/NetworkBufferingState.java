package reptor.distrbt.io.net;

import java.nio.ByteBuffer;


public class NetworkBufferingState
{

    private final ByteBuffer    m_sinkstate;
    private final ByteBuffer    m_sourcestate;


    public NetworkBufferingState(ByteBuffer sourcestate, ByteBuffer sinkstate)
    {
        m_sinkstate   = sinkstate;
        m_sourcestate = sourcestate;
    }


    public ByteBuffer getSinkState()
    {
        return m_sinkstate;
    }


    public ByteBuffer getSourceState()
    {
        return m_sourcestate;
    }

}
