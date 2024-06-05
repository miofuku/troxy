package reptor.distrbt.com.handshake;

import java.net.InetSocketAddress;


public class SocketConnectionArguments
{

    protected InetSocketAddress   m_addr;


    public SocketConnectionArguments(InetSocketAddress addr)
    {
        m_addr  = addr;
    }


    @Override
    public String toString()
    {
        return String.format( "-> %s", m_addr );
    }


    public InetSocketAddress getAddress()
    {
        return m_addr;
    }

}