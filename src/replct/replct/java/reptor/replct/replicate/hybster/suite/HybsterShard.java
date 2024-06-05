package reptor.replct.replicate.hybster.suite;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.trusted.TrustedCounterGroupCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
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
import reptor.replct.replicate.hybster.HybsterCertifiers;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages;
import reptor.replct.replicate.hybster.order.HybsterOrderShard;
import reptor.replct.replicate.hybster.order.HybsterOrdering;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages;
import reptor.replct.replicate.hybster.view.HybsterViewChangeShard;


public class HybsterShard extends ProtocolShardModule
                                    implements CheckpointShardContext,
                                               HybsterOrderShard.Context,
                                               HybsterViewChangeShard.Context,
                                               MessageHandler<Message>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final HybsterOrderShard         m_orderhandler;
    private final OnePhaseCheckpointShard   m_chkpthandler;
    private final HybsterViewChangeShard    m_vwchghandler;
    private final ClientShard               m_clinthandler;

    private final HybsterOrdering           m_ordering;
    private final boolean                   m_rotate;

    private final MessageMapper                             m_mapper;
    private final TrustedCounterGroupCertifier[]            m_ctrcertifs;
    private final GroupConnectionCertifier                  m_strcertifs;
    private final MessageVerifier<? super Command>          m_propverif;
    private final MulticastLink<? super NetworkMessage>     m_repchannel;
    private final boolean                                   m_indcliroute;

    private final ReplicaPeerGroup  m_grpconf;

    // TODO: Replace message queue for reconfiguration.
    //       1. Move relevant message in the current queue to a second one.
    //       2. From then, add new incoming RC messages to the second queue and all others to the first one.
    //       3. Process only the second queue.
    //       4. If the second queue is empty and a stable configuration reached, switch back to the main queue.
//    private final Queue<Message> m_reconfstore = new LinkedList<>();


    public HybsterShard(SchedulerContext<? extends SelectorDomainContext> master, short shardno,
                                  HybsterReplication repprot, HybsterCertifiers certs, ReplicaPeerGroup grpconf,
                                  OrderExtensions extmanager, ClientHandlingProcess cliproc,
                                  MulticastLink<? super NetworkMessage> repchannel,
                                  MessageMapper mapper)
    {
        super( master, shardno );

        // Shared
        m_mapper    = mapper;
        m_grpconf   = grpconf;
        m_ordering  = repprot.getOrdering();
        m_rotate    = m_ordering.getUseRotatingLeader();

        m_propverif  = certs.getProposalVerifier();
        m_strcertifs = certs.getStrongCertifier();
        m_ctrcertifs = certs.getCounterCertifiers();
        m_repchannel = repchannel;

        // Subordinates
        m_orderhandler = new HybsterOrderShard( this, shardno, m_ordering, extmanager );
        m_chkpthandler = new OnePhaseCheckpointShard( shardno, this, m_strcertifs, repprot.getCheckpointing() );
        m_vwchghandler = new HybsterViewChangeShard( this, shardno, viewno -> repprot.createView( viewno, grpconf ) );

        if( cliproc==null )
        {
            m_clinthandler = null;
            m_indcliroute  = false;
        }
        else
        {
            m_clinthandler = cliproc.createClientShard( this, shardno, mapper, repchannel );
            m_indcliroute  = cliproc.getClientHandling().getClientRoutingMode()==WorkerRoutingMode.INDIRECT;
        }
    }


    private boolean useRotatingLeader()
    {
        return m_rotate;
    }

    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "HYB%02d", m_shardno );
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    @Override
    public void initPeers(AgreementPeers peers)
    {
        m_orderhandler.initPeers( peers );
        m_chkpthandler.initPeers( peers );
        m_vwchghandler.initPeers( peers );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
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


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    // TODO: handleMessage should be synchronous!
    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
        case InvocationMessages.REQUEST_ID:
            m_orderhandler.enqueueMessage( msg );
            break;
        case CheckpointMessages.CHECKPOINT_ID:
        case CheckpointMessages.CHECKPOINT_CREATED_ID:
            m_chkpthandler.handleMessage( msg );
            break;
        case ViewChangeMessages.REQUEST_VIEW_CHANGE_ID:
        case ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_READY_ID:
        case HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID:
        case ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID:
        case ViewChangeMessages.NEW_VIEW_READY_ID:
        case HybsterViewChangeMessages.HYBSTER_NEW_VIEW_ID:
        case ViewChangeMessages.CONFIRM_NEW_VIEW_ID:
        case ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID:
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


    public void handleNewViewStable(StableView nv)
    {
        m_chkpthandler.initView( nv.getView() );

        if( m_indcliroute )
            m_clinthandler.enqueueMessage( nv );

        m_orderhandler.handleMessage( nv );
        m_vwchghandler.handleMessage( nv );
    }

    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public TrustedCounterGroupCertifier getOrderCertifier(byte proposer)
    {
        return m_ctrcertifs[ useRotatingLeader() ? proposer : 0 ];
    }


    @Override
    public TrustedCounterGroupCertifier getNewViewCertifier()
    {
        if( useRotatingLeader() )
            throw new NotImplementedException();

        return m_ctrcertifs[ 0 ];
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
    public ReplicaPeerGroup getPeerGroup()
    {
        return m_grpconf;
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


    @Override
    public HybsterOrdering getOrderingProtocol()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
