package reptor.replct;

import java.net.InetSocketAddress;


public class RemoteReplica
{

    private final byte                  m_repno;
    private final InetSocketAddress[]   m_addrs;
    private final int[]                 m_repaddrs;
    private final int[]                 m_cliaddrs;


    public RemoteReplica(byte repno, InetSocketAddress[] addrs, int[] repaddrs, int[] cliaddrs)
    {
        m_repno    = repno;
        m_addrs    = addrs;
        m_repaddrs = repaddrs;
        m_cliaddrs = cliaddrs;
    }


    public byte getReplicaNumber()
    {
        return m_repno;
    }


    public InetSocketAddress getAddress(int addrno)
    {
        return m_addrs[ addrno ];
    }


    public int getNumberOfAddressesForReplicas()
    {
        return m_addrs.length;
    }


    public InetSocketAddress getAddressForReplicas(int repaddrno)
    {
        return m_addrs[ m_repaddrs[ repaddrno ] ];
    }


    public int getNumberOfAddressesForClients()
    {
        return m_cliaddrs.length;
    }


    public InetSocketAddress getAddressForClients(int cliaddrno)
    {
        return m_addrs[ m_cliaddrs[ cliaddrno ] ];
    }

}
