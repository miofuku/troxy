package reptor.replct.replicate;

import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.View;


public class StaticView implements View
{

    private final int               m_number;
    private final Replication       m_replicate;
    private final ReplicaPeerGroup  m_repgroup;


    public StaticView(int viewno, Replication replicate, ReplicaPeerGroup repgroup)
    {
        m_number    = viewno;
        m_replicate = replicate;
        m_repgroup  = repgroup;
    }


    @Override
    public ReplicaPeerGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    @Override
    public final int getNumber()
    {
        return m_number;
    }


    @Override
    public final byte getCoordinator(int viewno)
    {
        return m_replicate.getCoordinator( viewno );
    }

}
