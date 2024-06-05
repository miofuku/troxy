package reptor.distrbt.io.net;


public class BufferedNetworkState
{

    private final NetworkState          m_netstate;
    private final NetworkBufferingState m_netbufstate;


    public BufferedNetworkState(NetworkState netstate, NetworkBufferingState netbufstate)
    {
        m_netstate    = netstate;
        m_netbufstate = netbufstate;
    }


    public NetworkState getNetworkState()
    {
        return m_netstate;
    }


    public NetworkBufferingState getNetworkBufferingState()
    {
        return m_netbufstate;
    }

}
