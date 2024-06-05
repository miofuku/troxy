package reptor.replct.common.quorums;


public class IntersectingQuorumDefinition implements QuorumDefinition
{

    private final int m_ffac;


    public IntersectingQuorumDefinition(int ffac)
    {
        m_ffac = ffac;
    }


    @Override
    public int tolerableFaults(int nprocs)
    {
        return ( nprocs-1 ) / ( m_ffac==0 ? 2 : 3*m_ffac );
    }


    @Override
    public int minimumProcesses(int nfaults)
    {
        return ( m_ffac+2 )*nfaults + 1;
    }


    @Override
    public boolean isGroupSupported(int nprocs, int nfaults)
    {
        // TODO: Should we consider nfaults here? (nprocs>=qs+nfaults)
        //       What does supported mean? These arguments are invalid (see FixedQuorumDefinition)
        //       or the resulting quorum sizes do not work with nfault considered?
        return nprocs>=upperQuorumSize( nprocs, nfaults );
    }


    @Override
    public int upperQuorumSize(int nprocs, int nfaults)
    {
        return ( base( nprocs, nfaults )+1 ) / 2;
    }


    @Override
    public int lowerQuorumSize(int nprocs, int nfaults)
    {
        return base( nprocs, nfaults ) / 2;
    }


    @Override
    public boolean isQuorumSizeSupported(int nprocs, int nfaults, int quorumsize)
    {
        if( quorumsize<=0 || quorumsize>nprocs )
            return false;
        else
        {
            int cs = counterQuorumSize( nprocs, nfaults, quorumsize );
            return cs>0 && cs<=nprocs;
        }
    }


    @Override
    public int counterQuorumSize(int nprocs, int nfaults, int quorumsize)
    {
        return base( nprocs, nfaults ) - quorumsize;
    }


    private int base(int nprocs, int nfaults)
    {
        return nprocs+m_ffac*nfaults+1;
    }

}
