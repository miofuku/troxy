package reptor.replct.invoke;

import reptor.chronos.Immutable;
import reptor.replct.common.WorkDistribution;

@Immutable
public class ClientToWorkerAssignment
{

    private final short             m_nreplicas;
    private final short             m_nclients;
    private final int               m_nclintshards;
    private final int[][]           m_clint_to_addrs;
//    private final WorkDistribution  m_workerdist;
    private final WorkDistribution  m_contactdist;


    public ClientToWorkerAssignment(short nreplicas, short nclients, int[][] clint_to_addrs, boolean distcontact)
    {
        m_nreplicas      = nreplicas;
        m_nclients       = nclients;
        m_nclintshards   = clint_to_addrs.length;
        m_clint_to_addrs = clint_to_addrs;
//        m_workerdist     = new WorkDistribution.RoundRobin( m_nclintshards );
//        m_contactdist    = distcontact ? new WorkDistribution.Blockwise( m_nreplicas, m_nclintshards ) : null;
	m_contactdist    = distcontact ? new WorkDistribution.RoundRobin( m_nreplicas ) : null;
    }


    public short getNumberOfClients()
    {
        return m_nclients;
    }


    public byte getContactForClient(short clino, byte coord)
    {
        return m_contactdist!=null ? (byte) m_contactdist.getStageForUnit( clino ) : coord;
    }


    public short getClientForLocalSequence(byte contact, short seqno)
    {
        if( m_contactdist==null )
            return (short) ( seqno + m_nreplicas );
        else
            return (short) m_contactdist.getUnitForSlot( contact, seqno+m_contactdist.getSlotForUnit( contact, m_nreplicas ) );
    }


    public short getShardForClient(short clino)
    {
//        m_workerdist.getStageForUnit( clino-m_nreplicas );
        return (short) ( ( clino-m_nreplicas ) % m_nclintshards );
    }


    public int[] getAddressesForClientShard(short clintshard)
    {
        return m_clint_to_addrs[ clintshard ];
    }


    public int getWorkerForClient(short clintshard, short clino)
    {
//        m_workerdist.getSlotForLocalUnit( clintshard, clino-m_nreplicas );
        return ( clino-m_nreplicas ) / m_nclintshards;
    }


    public short getClientForWorker(short clintshard, int wrkno)
    {
//        m_workerdist.getUnitForSlot( clintshard, wrkno ) + m_nreplicas;
        return (short) ( m_nreplicas + wrkno*m_nclintshards + clintshard );
    }


    public int getNumberOfWorkersForShard(short clintshard)
    {
        return m_nclients/m_nclintshards + ( clintshard<( m_nclients % m_nclintshards ) ? 1 : 0 );
    }


    public int getNumberOfShards()
    {
        return m_nclintshards;
    }

}
