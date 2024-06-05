package reptor.distrbt.io.net;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;


public class NetworkState
{

    private final SocketChannel m_channel;
    private final SelectionKey  m_selkey;


    public NetworkState(SocketChannel channel, SelectionKey selkey)
    {
        m_channel = Objects.requireNonNull( channel );
        m_selkey  = selkey;
    }


    public SocketChannel getChannel()
    {
        return m_channel;
    }


    public SelectionKey getSelectionKey()
    {
        return m_selkey;
    }

}
