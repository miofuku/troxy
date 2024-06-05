package reptor.replct;

import java.net.InetSocketAddress;


public class ReplicaPeerGroup extends ReplicaGroup
{

    private final byte m_repno;


    public ReplicaPeerGroup(byte repno, byte nreplicas, byte nfaults)
    {
        super( nreplicas, nfaults );

        m_repno = repno;
    }


    public ReplicaPeerGroup(byte repno, ReplicaGroup repgroup)
    {
        this( repno, repgroup.getReplicas(), repgroup.getNumberOfTolerableFaults() );
    }


    public ReplicaPeerGroup(byte repno, RemoteReplica[] replicas, byte nfaults)
    {
        super( replicas, nfaults );

        m_repno = repno;
    }


    public byte getReplicaNumber()
    {
        return m_repno;
    }


    public RemoteReplica getLocal()
    {
        return getReplica( m_repno );
    }


    public InetSocketAddress getAddress(int addrno)
    {
        return getLocal().getAddress( addrno );
    }

}
