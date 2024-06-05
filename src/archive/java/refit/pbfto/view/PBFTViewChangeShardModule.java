package refit.pbfto.view;

import refit.pbfto.suite.PBFT;
import reptor.chronos.ChronosTask;
import reptor.chronos.SchedulerContext;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.common.modules.ProtocolShardModule;


public class PBFTViewChangeShardModule extends ProtocolShardModule implements MessageHandler<Message>
{

    private final PBFTViewChangeShard m_viewshard;


    public PBFTViewChangeShardModule(SchedulerContext<? extends SelectorDomainContext> master,
            short no, PBFT protocol, MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( master, no );

        m_viewshard = new PBFTViewChangeShard( master.getDomainContext(), protocol, no, reptrans );
    }

    @Override
    public String toString()
    {
        return String.format( "VWC%02d", m_shardno );
    }

    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        m_viewshard.handleMessage( msg );

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
        if( m_viewshard.isReady() )
            m_viewshard.execute();
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }

}
