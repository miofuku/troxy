package reptor.replct.connect;

import java.net.InetSocketAddress;
import java.util.Objects;

import reptor.distrbt.com.handshake.SocketConnectionArguments;


public class StandardConnectionArguments extends SocketConnectionArguments
{

    private final short             m_locno;
    private final RemoteEndpoint    m_remep;


    public StandardConnectionArguments(short locno, RemoteEndpoint remep, InetSocketAddress addr)
    {
        super( addr );

        m_locno = locno;
        m_remep = Objects.requireNonNull( remep );
    }


    @Override
    public String toString()
    {
        return String.format( "%d -> %d (%s, %d)", m_locno, m_remep.getProcessNumber(), m_addr, m_remep.getNetworkNumber() );
    }


    public RemoteEndpoint getRemoteEndpoint()
    {
        return m_remep;
    }

}