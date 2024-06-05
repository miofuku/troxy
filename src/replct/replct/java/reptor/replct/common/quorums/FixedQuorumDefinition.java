package reptor.replct.common.quorums;

import com.google.common.base.Preconditions;

public class FixedQuorumDefinition implements QuorumDefinition
{

    private final int m_nprocs;
    private final int m_nfaults;
    private final int m_upperqs;
    private final int m_lowerqs;


    public FixedQuorumDefinition(int nprocs, int nfaults, int upperqs, int lowerqs)
    {
        m_nprocs  = nprocs;
        m_nfaults = nfaults;
        m_upperqs = upperqs;
        m_lowerqs = lowerqs;
    }


    @Override
    public int tolerableFaults(int nprocs)
    {
        Preconditions.checkArgument( nprocs==m_nprocs );

        return m_nfaults;
    }


    @Override
    public int minimumProcesses(int nfaults)
    {
        Preconditions.checkArgument( nfaults==m_nfaults );

        return m_nprocs;
    }


    @Override
    public boolean isGroupSupported(int nprocs, int nfaults)
    {
        checkGroup( nprocs, nfaults );

        return true;
    }


    @Override
    public int upperQuorumSize(int nprocs, int nfaults)
    {
        checkGroup( nprocs, nfaults );

        return m_upperqs;
    }


    @Override
    public int lowerQuorumSize(int nprocs, int nfaults)
    {
        checkGroup( nprocs, nfaults );

        return m_lowerqs;
    }


    @Override
    public boolean isQuorumSizeSupported(int nprocs, int nfaults, int quorumsize)
    {
        checkQuorumSize( nprocs, nfaults, quorumsize );

        return true;
    }


    @Override
    public int counterQuorumSize(int nprocs, int nfaults, int quorumsize)
    {
        checkQuorumSize( nprocs, nfaults, quorumsize );

        return quorumsize==m_upperqs ? m_lowerqs : m_upperqs;
    }


    private void checkGroup(int nprocs, int nfaults)
    {
        Preconditions.checkArgument( nprocs==m_nprocs );
        Preconditions.checkArgument( nfaults==m_nfaults );
    }


    private void checkQuorumSize(int nprocs, int nfaults, int quorumsize)
    {
        checkGroup( nprocs, nfaults );
        Preconditions.checkArgument( quorumsize==m_upperqs || quorumsize==m_lowerqs );
    }

}
