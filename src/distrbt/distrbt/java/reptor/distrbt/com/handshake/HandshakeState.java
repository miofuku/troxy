package reptor.distrbt.com.handshake;

import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;


public class HandshakeState
{

    private final BufferedNetworkState  m_netstate;
    private final SslState              m_sslstate;
    private final boolean               m_isconnector;


    public HandshakeState(boolean isconnector, BufferedNetworkState netstate, SslState sslstate)
    {
        m_isconnector = isconnector;
        m_netstate    = netstate;
        m_sslstate    = sslstate;
    }


    public boolean isConnector()
    {
        return m_isconnector;
    }


    public BufferedNetworkState getBufferedNetworkState()
    {
        return m_netstate;
    }


    public SslState getSslState()
    {
        return m_sslstate;
    }

}
