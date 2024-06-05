package reptor.replct;


public class ReplicaGroup
{

    private final byte              m_nreplicas;
    private final RemoteReplica[]   m_replicas;
    private final byte              m_nfaults;


    public ReplicaGroup(byte nreplicas, byte nfaults)
    {
        m_nreplicas = nreplicas;
        m_replicas  = null;
        m_nfaults   = nfaults;
    }


    public ReplicaGroup(RemoteReplica[] replicas, byte nfaults)
    {
        m_nreplicas = (byte) replicas.length;
        m_replicas  = replicas;
        m_nfaults   = nfaults;
    }


    public byte size()
    {
        return m_nreplicas;
    }


    public RemoteReplica getReplica(byte repno)
    {
        return m_replicas[ repno ];
    }


    public RemoteReplica[] getReplicas()
    {
        return m_replicas;
    }


    public byte getNumberOfTolerableFaults()
    {
        return m_nfaults;
    }

}
