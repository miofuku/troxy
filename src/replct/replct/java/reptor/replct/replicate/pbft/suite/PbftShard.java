package reptor.replct.replicate.pbft.suite;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.checkpoint.CheckpointMessages;
import reptor.replct.agree.checkpoint.OnePhaseCheckpointShard;
import reptor.replct.agree.checkpoint.OnePhaseCheckpointShard.CheckpointShardContext;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.view.StableView;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.clients.ClientHandlingProcess;
import reptor.replct.clients.ClientShard;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.common.modules.WorkerRoutingMode;
import reptor.replct.connect.CommunicationMessages;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.replicate.pbft.PbftCertifiers;
import reptor.replct.replicate.pbft.order.PbftOrderMessages;
import reptor.replct.replicate.pbft.order.PbftOrderShard;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages;
import reptor.replct.replicate.pbft.view.PbftViewChangeShard;


public class PbftShard extends ProtocolShardModule
                                    implements CheckpointShardContext,
                                               PbftOrderShard.Context,
                                               MessageHandler<Message>
{

    private final PbftOrderShard            m_orderhandler;
    private final OnePhaseCheckpointShard   m_chkpthandler;
    private final PbftViewChangeShard       m_vwchghandler;
    private final ClientShard               m_clinthandler;

    private final MessageMapper                             m_mapper;
    private final GroupConnectionCertifier                  m_stdcertif;
    private final MessageVerifier<? super Command>          m_propverif;
    private final MulticastLink<? super NetworkMessage>     m_repchannel;
    private final boolean                                   m_indcliroute;

    // TODO: Replace message queue for reconfiguration.
    //       1. Move relevant message in the current queue to a second one.
    //       2. From then, add new incoming RC messages to the second queue and all others to the first one.
    //       3. Process only the second queue.
    //       4. If the second queue is empty and a stable configuration reached, switch back to the main queue.
//    private final Queue<Message> m_reconfstore = new LinkedList<>();


    public PbftShard(SchedulerContext<? extends SelectorDomainContext> master, short shardno,
                               PbftReplication repprot, PbftCertifiers certs, ReplicaPeerGroup repgroup,
                               OrderExtensions extmanager, ClientHandlingProcess cliproc,
                               MulticastLink<? super NetworkMessage> repconn,
                               MessageMapper mapper)
    {
        super( master, shardno );

        // Shared
        m_mapper   = mapper;

        m_propverif  = certs.getProposalVerifier();
        m_stdcertif  = certs.getStandardCertifier();
        m_repchannel = repconn;

        // Subordinates
        m_orderhandler = new PbftOrderShard( this, shardno, repprot.getOrdering(), extmanager );
        m_chkpthandler = new OnePhaseCheckpointShard( shardno, this, m_stdcertif, repprot.getCheckpointing() );
        m_vwchghandler = new PbftViewChangeShard( shardno );

        if( cliproc==null )
        {
            m_clinthandler = null;
            m_indcliroute  = false;
        }
        else
        {
            m_clinthandler = cliproc.createClientShard( this, shardno, mapper, repconn );
            m_indcliroute  = cliproc.getClientHandling().getClientRoutingMode()==WorkerRoutingMode.INDIRECT;
        }
    }


    @Override
    public String toString()
    {
        return String.format( "HYB%02d", m_shardno );
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case PbftOrderMessages.PBFT_PREPREPARE_ID:
        case PbftOrderMessages.PBFT_PREPARE_ID:
        case PbftOrderMessages.PBFT_COMMIT_ID:
        case InvocationMessages.REQUEST_ID:
            m_orderhandler.enqueueMessage( msg );
            break;
        case CheckpointMessages.CHECKPOINT_ID:
        case CheckpointMessages.CHECKPOINT_CREATED_ID:
            m_chkpthandler.handleMessage( msg );
            break;
        case PbftViewChangeMessages.PBFT_VIEW_CHANGE_ID:
        case PbftViewChangeMessages.PBFT_NEW_VIEW_ID:
            m_vwchghandler.handleMessage( msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            m_orderhandler.enqueueMessage( msg );
            m_chkpthandler.handleMessage( msg );
            break;
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleNewViewStable( (StableView) msg );
            break;
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
            m_orderhandler.handleMessage( msg );
            m_chkpthandler.handleMessage( msg );
            m_vwchghandler.handleMessage( msg );
            break;
        case CommunicationMessages.NEW_CONNECTION_ID:
        case InvocationMessages.REQUEST_EXECUTED_ID:
        case InvocationMessages.REPLY_ID:
            m_clinthandler.enqueueMessage( msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }

        return false;
    }


    @Override
    public void initPeers(AgreementPeers peers)
    {
        m_orderhandler.initPeers( peers );
        m_chkpthandler.initPeers( peers );
        m_vwchghandler.initPeers( peers );
    }


    public void handleNewViewStable(StableView nv)
    {
        m_chkpthandler.initView( nv.getView() );

        if( m_indcliroute )
            m_clinthandler.enqueueMessage( nv );

        m_orderhandler.handleMessage( nv );
        m_vwchghandler.handleMessage( nv );
    }


    @Override
    protected void executeSubjects()
    {
        if( m_clinthandler==null )
        {
            if( m_orderhandler.isReady() )
                while( !m_orderhandler.execute() ) ;
        }
        else
        {
            while( m_clinthandler.isReady() || m_orderhandler.isReady() )
            {
                if( m_clinthandler.isReady() )
                    while( !m_clinthandler.execute() ) ;

                if( m_orderhandler.isReady() )
                   while( !m_orderhandler.execute() ) ;
            }
        }
    }


    @Override
    public GroupConnectionCertifier getStandardCertifier()
    {
        return m_stdcertif;
    }


    @Override
    public MessageVerifier<? super Command> getProposalVerifier()
    {
        return m_propverif;
    }


    @Override
    public MessageMapper getMessageMapper()
    {
        return m_mapper;
    }


    @Override
    public MulticastLink<? super NetworkMessage> getReplicaChannel()
    {
        return m_repchannel;
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }

}
