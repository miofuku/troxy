package reptor.replct.common.modules;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.agree.AgreementPeers;


public abstract class ProtocolShardModule extends PublicMasterActor
{
    protected final short m_shardno;


    public ProtocolShardModule(SchedulerContext<? extends SelectorDomainContext> master, short no)
    {
        super( master, null );

        m_shardno = no;
    }


    public final int getNumber()
    {
        return m_shardno;
    }


    abstract public void initPeers(AgreementPeers peers);

}
