package reptor.replct.common.quorums;


public class FBasedQuorumDefinition implements QuorumDefinition
{

    private final int m_ffac;


    public FBasedQuorumDefinition(int ffac)
    {
        m_ffac = ffac;
    }


    @Override
    public int tolerableFaults(int nprocs)
    {
        return m_ffac==0 ? nprocs : ( nprocs-1 ) / m_ffac;
    }


    @Override
    public int minimumProcesses(int nfaults)
    {
        return m_ffac*nfaults+1;
    }


    @Override
    public boolean isGroupSupported(int nprocs, int nfaults)
    {
        return nprocs>=upperQuorumSize( nprocs, nfaults );
    }


    @Override
    public int upperQuorumSize(int nprocs, int nfaults)
    {
        return m_ffac*nfaults+1;
    }


    @Override
    public int lowerQuorumSize(int nprocs, int nfaults)
    {
        return upperQuorumSize( nprocs, nfaults );
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
        return upperQuorumSize( nprocs, nfaults );
    }

}
