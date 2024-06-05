package reptor.replct.replicate.pbft.order;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.jlib.collect.FixedSlidingCollectionWindow;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.jlib.collect.SlidingCollectionWindow;
import reptor.jlib.collect.UnboundSlidingCollectionWindow;
import reptor.jlib.collect.mark.RangeWindowMarker;
import reptor.jlib.collect.mark.SlidingWindowMarker;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.ViewLogging;
import reptor.replct.agree.checkpoint.CheckpointLogging;
import reptor.replct.agree.checkpoint.CheckpointMessages;
import reptor.replct.agree.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.order.OrderMessages.CommandBatch;
import reptor.replct.agree.order.OrderMessages.CommandOrdered;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.common.WorkDistribution;
import reptor.replct.replicate.pbft.view.PbftViewChangeHandler;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages.PbftNewViewStable;


public class PbftOrderShard implements ProtocolHandler, MessageHandler<Message>,
                                        Actor, PushMessageSink<Message>,
                                        PbftOrderInstance.Context
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context
    {
        ChronosDomainContext getDomainContext();

        MessageMapper                               getMessageMapper();
        MulticastLink<? super NetworkMessage>       getReplicaChannel();
        GroupConnectionCertifier                    getStandardCertifier();
        MessageVerifier<? super Command>            getProposalVerifier();
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( PbftOrderShard.class );

    private final Context               m_cntxt;
    private final short                 m_shardno;
    private final WorkDistribution  m_instdist;
    private final PbftOrdering          m_config;

    private final PbftViewChangeHandler m_vwchghandler;

    private long m_wndstart;
    private long m_wndend;
    private long m_nextproplocno;

    private long      m_enqueuedchkpt     = Long.MIN_VALUE; // The first checkpoint has the order number -1.
    private boolean   m_isforwardready    = false;
    private final SlidingWindowMarker<PbftOrderInstance> m_readyslots;

    private final Queue<Command>                                m_proposals;
    private final FixedSlidingWindow<PbftOrderInstance>         m_actinsts;
    private final SlidingCollectionWindow<OrderNetworkMessage>  m_backlog;
    private final Map<Integer, Collection<OrderNetworkMessage>> m_futuremsgs = new HashMap<>();

    private final boolean m_useunboundwnd;

    // Configuration dependent
    private PushMessageSink<Message> m_orderlearner;

    // View dependent
    private View m_stableview = null;
    private int  m_curviewno  = -1;
    private byte m_repno      = -1;
    private byte m_proposer   = -1;


    public PbftOrderShard(Context cntxt, short shardno, PbftOrdering config, OrderExtensions extmanager)
    {
        // Configuration.
        m_cntxt     = cntxt;
        m_shardno   = shardno;
        m_config    = config;

        m_instdist      = m_config.getOrderInstanceShardDistribution();
        m_useunboundwnd = m_config.getUseUnboundOrderWindow();

        // Members and subjects
        m_wndstart      = -activeWindowSize();
        m_wndend        = useUnboundWindow() ? Long.MAX_VALUE : m_wndstart + windowSize();
        m_nextproplocno = 0;

        m_proposals  = new ArrayDeque<>();

        m_actinsts   = new FixedSlidingWindow<>( PbftOrderInstance.class, activeWindowSizeForShard(),
                                                 i -> new PbftOrderInstance( this, config, extmanager.getProtocolInstanceObserver( shardno, i ) ),
                                                 -activeWindowSizeForShard() );
        m_readyslots = new RangeWindowMarker<>( m_actinsts, t -> t.isReady() );

        int blsize = windowSizeForShard()-activeWindowSizeForShard();
        if( useUnboundWindow() )
            m_backlog = new UnboundSlidingCollectionWindow<OrderNetworkMessage>( blsize );
        else
            m_backlog = new FixedSlidingCollectionWindow<OrderNetworkMessage>( blsize );

        m_vwchghandler = new PbftViewChangeHandler( cntxt.getDomainContext(), shardno );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainContext().getDomainAddress();
    }


    private MulticastLink<? super NetworkMessage> replicaChannel()
    {
        return m_cntxt.getReplicaChannel();
    }

    private boolean useUnboundWindow()
    {
        return m_useunboundwnd;
    }

    private int windowSize()
    {
        return m_config.getOrderWindowSize();
    }

    private int windowSizeForShard()
    {
        return m_config.getOrderWindowSizeForShard();
    }

    private int activeWindowSize()
    {
        return m_config.getActiveOrderWindowSize();
    }

    private int activeWindowSizeForShard()
    {
        return m_config.getActiveOrderWindowSizeForShard();
    }

    private int minumumBatchSize()
    {
        return m_config.getMinumumCommandBatchSize();
    }

    private int maximumBatchSize()
    {
        return m_config.getMaximumCommandBatchSize();
    }

    private int executor()
    {
        return m_config.getLinkedExecutorForOrderShard( m_shardno );
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "ODR%02d", m_shardno );
    }


    public final short getShardNumber()
    {
        return m_shardno;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initPeers(AgreementPeers peers)
    {
        m_orderlearner = peers.getExecutors().get( executor() ).createChannel( domain() );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case PbftOrderMessages.PBFT_PREPREPARE_ID:
        case PbftOrderMessages.PBFT_PREPARE_ID:
        case PbftOrderMessages.PBFT_COMMIT_ID:
            enqueueOrderMessage( (OrderNetworkMessage) msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            enqueueCheckpointStable( (CheckpointStable) msg );
            break;
        default:
            enqueueComand( (Command) msg );
        }
    }


    private void enqueueComand(Command cmd)
    {
        m_proposals.add( cmd );

        if( isCurrentViewStable() && m_repno!=m_proposer )
            m_isforwardready = true;
    }


    private void enqueueOrderMessage(OrderNetworkMessage msg)
    {
        if( msg.getViewNumber()<m_curviewno || msg.getOrderNumber()<m_wndstart )
            s_logger.debug( "{} drop outdated message {}", this, msg );
        else if( msg.getOrderNumber()>=m_wndend )
        {
            throw new UnsupportedOperationException( "Message above the current window " +
                                                     m_wndstart + "-" + m_wndend + ": " + msg );
        }
        else if( !isCurrentViewStable() || msg.getViewNumber()>m_curviewno )
        {
            Collection<OrderNetworkMessage> msgs = m_futuremsgs.get( msg.getViewNumber() );

            if( msgs==null )
            {
                msgs = new ArrayList<>();
                m_futuremsgs.put( msg.getViewNumber(), msgs );
            }

            msgs.add( msg );
        }
        else
        {
            long locno = m_instdist.getSlotForLocalUnit( m_shardno, msg.getOrderNumber() );

            if( locno>=m_backlog.getWindowStart() )
                m_backlog.add( locno, msg );
            else
            {
                m_actinsts.getSlotUnchecked( locno ).enqueueMessage( msg );
                m_readyslots.add( locno );
            }
        }
    }


    private void enqueueCheckpointStable(CheckpointStable msg)
    {
        m_enqueuedchkpt = Math.max( m_enqueuedchkpt, msg.getOrderNumber() );
    }


    @Override
    public boolean isReady()
    {
        return m_nextproplocno<m_actinsts.getWindowEnd() && isProposalReady() ||
               !m_readyslots.isEmpty() || m_enqueuedchkpt!=Long.MIN_VALUE || m_isforwardready;
    }


    @Override
    public boolean execute()
    {
        assert isReady();

        if( m_enqueuedchkpt!=Long.MIN_VALUE )
            processCheckpoint();

        if( !m_readyslots.isEmpty() )
            processReadyInstances();

        processProposals();

        if( m_isforwardready )
            forwardProposals();

        return !isReady();
    }


    private void processReadyInstances()
    {
        PbftOrderInstance inst;

        while( ( inst = m_readyslots.poll() )!=null )
        {
            boolean iscom = inst.isCommitted();

            inst.execute();

            if( !iscom && inst.isCommitted() )
                instanceCompleted( inst );
        }
    }

    private void processProposals()
    {
        long wndend = m_actinsts.getWindowEnd();

        while( isProposalReady() && m_nextproplocno<wndend )
        {
            PbftOrderInstance inst = m_actinsts.getSlotUnchecked( m_nextproplocno++ );

            if( inst.isProposer() )
                assignProposals( inst );
        }

        if( s_logger.isDebugEnabled() && m_proposals.size()>0 )
            s_logger.debug( "{} no instances available to propose {} commands", this, m_proposals.size() );
    }


    private boolean isProposalReady()
    {
        return m_proposals.size()>=minumumBatchSize();
    }


    private void assignProposals(PbftOrderInstance inst)
    {
        if( maximumBatchSize()==1 || ( m_proposals.size()==1 ) )
            inst.handleCommand( m_proposals.poll() );
        else
        {
            int       bs   = Math.min( m_proposals.size(), maximumBatchSize() );
            Command[] cmds = new Command[ bs ];

            for( int i=0; i<bs; i++ )
                cmds[ i ] = m_proposals.poll();

            CommandBatch batch = new CommandBatch( m_repno, inst.getOrderNumber(), m_curviewno, cmds );
            batch.setValid();
            batch.setCertificateSize( 0 );

            inst.handleCommand( batch );
        }
    }


    private void instanceCompleted(PbftOrderInstance inst)
    {
        m_orderlearner.enqueueMessage( new CommandOrdered( inst.getProposer(), inst.getOrderNumber(), inst.getResult() ) );
    }


    private void processCheckpoint()
    {
        long chkptno = m_enqueuedchkpt;
        m_enqueuedchkpt = Long.MIN_VALUE;

        long newwndstart      = chkptno+1;
        long newwndstartlocno = m_instdist.getSlotForUnit( m_shardno, newwndstart );

        // Only move window forward
        if( newwndstartlocno<=m_actinsts.getWindowStart() )
            return;

        if( s_logger.isDebugEnabled( CheckpointLogging.MARKER ) )
        {
            s_logger.debug( CheckpointLogging.MARKER, "{} move window from {}-{} / {}-{} to {}-{} / {}-{}",
                            this, m_actinsts.getWindowStart(), m_wndstart,
                            m_actinsts.getWindowEnd(), m_wndstart+activeWindowSize(),
                            newwndstartlocno, newwndstart,
                            newwndstartlocno+m_actinsts.size(), newwndstart+activeWindowSize() );
        }

        m_wndstart = newwndstart;
        if( !useUnboundWindow() )
            m_wndend = m_wndstart + windowSize();
        m_nextproplocno = Math.max( m_nextproplocno, newwndstartlocno );

        // If the new start of the window is above the end of the previous active window, instances are skipped.
        if( newwndstartlocno>m_backlog.getWindowStart() )
            m_backlog.skipSlots( newwndstartlocno );

        long current = m_actinsts.forwardWindow( newwndstartlocno );
        long wndend  = m_actinsts.getWindowEnd();

        while( current<wndend )
        {
            PbftOrderInstance inst = m_actinsts.getSlotUnchecked( current );

            inst.init( m_stableview, m_instdist.getUnitForSlot( m_shardno, current ) );
            if( m_backlog.forwardWindow( inst::enqueueMessage ) )
                m_readyslots.add( current );

            current++;
        }

        m_readyslots.removeTo( newwndstartlocno );
    }


    private void forwardProposals()
    {
        for( Command cmd : m_proposals )
            replicaChannel().enqueueUnicast( m_proposer, cmd );

        m_proposals.clear();
        m_isforwardready = false;
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleViewChange( msg );
            break;

        default:
            enqueueMessage( msg );

            if( isReady() )
                execute();
            break;
        }

        // TODO: The external state of order shards are completed order instances!
        return false;
    }


    public void handleViewChange(Message msg)
    {
        if( !m_vwchghandler.handleMessage( msg ) )
            return;

        if( m_vwchghandler.isViewChangeInitiated() )
        {
//            m_curviewno  = m_vchandler.getViewNumber();
//            m_stableview = null;
//
//            m_vchandler.prepareOrderShard( ordershard.abortView( new OrderInstanceIterator() ) );
        }
        else
        {
            assert m_vwchghandler.isViewStable();

            initStableView( m_vwchghandler.getStableView() );
        }
    }


    public void initStableView(PbftNewViewStable nv)
    {
        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, nv.getViewNumber() );

        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();

        // The first view change is taken as a change of the group composition.
        if( m_curviewno==1 )
        {
            m_repno    = m_stableview.getReplicaGroup().getReplicaNumber();
            m_proposer = m_config.getContact( m_curviewno, m_repno );
        }

        // TODO: m_prewndendlocno, m_isforwardready, m_isunprepready?
        //m_nextproplocno
        // TODO: Clear backlog

        // Init instances for the new view
//      if( nv.getViewNumber()!=0 )
//          ordershard.initView( new OrderInstanceIterator(), nv );

      // Insert message stored for the new view.
//      Collection<OrderProtocolMessage> msgs = m_futuremsgs.remove( m_curviewno );
//
//      if( msgs!=null )
//      {
//          for( OrderProtocolMessage m : msgs )
//              dispatchOrderMessage( m );
//      }
//
//      m_futuremsgs.keySet().removeIf( viewno -> viewno<=m_curviewno );
//
//      // Transfer proposals to new context.
//      if( replicaid!=m_stableview.getContactReplicaID() && proposals.size()>0 )
//      {
//          m_statetrans.transferMessages( m_stableview.getContactReplicaID(), new ArrayList<>( proposals ) );
//          proposals.clear();
//      }
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private boolean isCurrentViewStable()
    {
        // TODO: Use initial object (-1)?
        return m_stableview!=null && m_curviewno==m_stableview.getNumber();
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public MessageMapper getMessageMapper()
    {
        return m_cntxt.getMessageMapper();
    }


    @Override
    public PushMessageSink<? super OrderNetworkMessage> getReplicaChannel()
    {
        return m_cntxt.getReplicaChannel();
    }


    @Override
    public GroupConnectionCertifier getStandardCertifier()
    {
        return m_cntxt.getStandardCertifier();
    }


    @Override
    public MessageVerifier<? super Command> getProposalVerifier()
    {
        return m_cntxt.getProposalVerifier();
    }

}
