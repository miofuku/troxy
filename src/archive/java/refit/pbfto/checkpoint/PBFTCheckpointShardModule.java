package refit.pbfto.checkpoint;

import refit.pbfto.suite.PBFT;
import reptor.chronos.ChronosTask;
import reptor.chronos.SchedulerContext;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.common.modules.ProtocolShardModule;


public class PBFTCheckpointShardModule extends ProtocolShardModule implements MessageHandler<Message>
{

    private final PBFTCheckpointShard m_chkptshard;


    public PBFTCheckpointShardModule(SchedulerContext<? extends SelectorDomainContext> master,
            short no, PBFT reprot, ReplicaPeerGroup grpconf, MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( master, no );

        m_chkptshard = new PBFTCheckpointShard( master.getDomainContext(), reprot, no, grpconf, reptrans );
    }


    @Override
    public String toString()
    {
        return String.format( "CHK%02d", m_shardno );
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        m_chkptshard.handleMessage( msg );

        return false;
    }

    @Override
    public void initPeers(AgreementPeers peers)
    {
        // TODO Auto-generated method stub

    }

    @Override
    protected void executeSubjects()
    {

    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }

}
