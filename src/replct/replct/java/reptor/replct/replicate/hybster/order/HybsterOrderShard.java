package reptor.replct.replicate.hybster.order;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import reptor.distrbt.certify.trusted.TrustedCounterGroupCertifier;
import reptor.distrbt.certify.trusted.TrustedCounterValue;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.jlib.NotImplementedException;
import reptor.jlib.collect.FixedSlidingCollectionWindow;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.jlib.collect.SlidingCollectionWindow;
import reptor.jlib.collect.UnboundSlidingCollectionWindow;
import reptor.jlib.collect.mark.SlidingWindowMarker;
import reptor.jlib.collect.mark.TreeWindowMarker;
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
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterPrepare;
import reptor.replct.replicate.hybster.view.HybsterViewChangeHandler;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewView;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewViewStable;


public class HybsterOrderShard implements ProtocolHandler, MessageHandler<Message>,
                                           Actor, PushMessageSink<Message>,
                                           HybsterOrderInstance.Context
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context
    {
        ChronosDomainContext getDomainContext();

        MessageMapper                               getMessageMapper();
        MulticastLink<? super NetworkMessage>       getReplicaChannel();
        TrustedCounterGroupCertifier                getOrderCertifier(byte proposer);
        MessageVerifier<? super Command>            getProposalVerifier();
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterOrderShard.class );

    private final Context                   m_cntxt;
    private final short                     m_shardno;
    private final WorkDistribution      m_instdist;
    private final HybsterOrdering           m_config;

    private final HybsterViewChangeHandler m_vwchghandler;

    private long m_wndstart;
    private long m_wndend;

    // TODO: Separate somehow. nextIterator and sequential counters for proposal.
    private long[]    m_prepwndendlocno; // (Exclusive) end of the window of prepared instances ->
                                         // first instance that is not yet prepared (according to the local sequence).
    private boolean[] m_isunprepready;
    private int       m_nreadyprops = 0;

    private long      m_enqueuedchkpt     = Long.MIN_VALUE; // The first checkpoint has the order number -1.
    private boolean   m_isforwardready    = false;
    private final SlidingWindowMarker<HybsterOrderInstance> m_readypreps;

    private final Queue<Command>                                m_proposals;
    private final FixedSlidingWindow<HybsterOrderInstance>      m_actinsts;
    private final SlidingCollectionWindow<OrderNetworkMessage>  m_backlog;
    private final Map<Integer, Collection<OrderNetworkMessage>> m_futuremsgs = new HashMap<>();

    private long m_orderno_last = 0;

    private final boolean m_useunboundwnd;
    private       int     m_propinststep;

    // Configuration dependent
    private PushMessageSink<Message> m_orderlearner;

    // View dependent
    private View m_stableview = null;
    private int  m_curviewno  = -1;
    private byte m_repno      = -1;
    private int  m_nreplicas  = -1;
    private byte m_proposer   = -1;


    public HybsterOrderShard(Context cntxt, short shardno, HybsterOrdering config, OrderExtensions extmanager)
    {
        // Configuration.
        m_cntxt     = cntxt;
        m_shardno   = shardno;
        m_config    = config;

        m_instdist      = m_config.getOrderInstanceShardDistribution();
        m_useunboundwnd = m_config.getUseUnboundOrderWindow();

        // Members and subjects
        m_wndstart        = -activeWindowSize();
        m_wndend          = useUnboundWindow() ? Long.MAX_VALUE : m_wndstart + windowSize();

        m_proposals  = new ArrayDeque<>();

        m_actinsts   = new FixedSlidingWindow<>( HybsterOrderInstance.class, activeWindowSizeForShard(),
                                                 i -> new HybsterOrderInstance( this, config, extmanager.getProtocolInstanceObserver( shardno, i ) ),
                                                 -activeWindowSizeForShard() );

        // TODO: Use range ready slot for each proposing replica -> requires sequential number for its proposals.
        m_readypreps = new TreeWindowMarker<>( m_actinsts );

        int blsize = windowSizeForShard()-activeWindowSizeForShard();
        if( useUnboundWindow() )
            m_backlog = new UnboundSlidingCollectionWindow<OrderNetworkMessage>( blsize );
        else
            m_backlog = new FixedSlidingCollectionWindow<OrderNetworkMessage>( blsize );

        m_vwchghandler = new HybsterViewChangeHandler( cntxt.getDomainContext(), shardno );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainContext().getDomainAddress();
    }

    private MulticastLink<? super NetworkMessage> replicaChannel()
    {
        return m_cntxt.getReplicaChannel();
    }

    private TrustedCounterGroupCertifier orderCertifier(byte proposer)
    {
        return m_cntxt.getOrderCertifier( proposer );
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

    private boolean useRotatingLeader()
    {
        return m_config.getUseRotatingLeader();
    }

    private int executor()
    {
        return m_config.getLinkedExecutorForOrderShard( m_shardno );
    }

    private long nextLocalNumberForProposer(byte proposer, long curlocno)
    {
        return curlocno + m_propinststep;
    }

    private int forcedViewChangeInterval()
    {
        return m_config.getForcedViewChangeInterval();
    }

    private int forcedViewChangeTimeout()
    {
        return m_config.getForcedViewChangeTimeout();
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

        m_vwchghandler.initPeers( peers );
    }


    public void abortView(int nextviewno)
    {
        // TODO: Use checkpoint interval as initial capacity?
        ArrayList<OrderNetworkMessage> prepmsgs = new ArrayList<>();
        long orderno_last = m_orderno_last;
        int  nuncertains  = 0;

        long current = m_actinsts.getWindowStart();
        long wndend  = m_actinsts.getWindowEnd();

        while( current<wndend )
        {
            HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( current );

            if( inst.isPrepared() && !inst.isCommitted() && inst.isProposer() )
                nuncertains++;

            OrderNetworkMessage prep = inst.preparedCertificate();

            if( prep!=null )
                prepmsgs.add( prep );

            OrderNetworkMessage own = inst.getOwnMessage();

            if( own!=null )
                orderno_last = inst.getOrderNumber();

            current++;
        }

        s_logger.debug( ViewLogging.MARKER, "{} abort view {} with {} prepared and {} uncertain instances and " +
                        "last orderno {} for view {}", this, m_curviewno, prepmsgs.size(), nuncertains, orderno_last, nextviewno );

        clearAbortedView();

        m_curviewno = nextviewno;

        m_vwchghandler.prepareOrderShard( prepmsgs.toArray( new OrderNetworkMessage[ prepmsgs.size() ] ), orderno_last );
    }


    private void clearAbortedView()
    {
        clearAllProposerWindows();
        m_readypreps.clear();
        m_backlog.clear();
    }


    // TODO: The counter values of view for which no own view change has been sent must be invalidated.
    //       Btw. who is responsible for the order counter? It should be the order shard. Then, the View Change
    //       should certified by the view handler of the order shard. Or views are coordinated by the master
    //       shard.
    public void initStableView(HybsterNewViewStable nv)
    {
        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, nv.getViewNumber() );

        // Was a view learned passively? Then we have to clean up the old one.
        if( isCurrentViewStable() )
            clearAbortedView();

        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();

        // The first view change is taken as a change of the group composition.
        if( m_curviewno==1 )
        {
            m_repno     = m_stableview.getReplicaNumber();
            m_nreplicas = m_stableview.getNumberOfReplicas();
            m_proposer  = m_config.getContact( m_curviewno, m_repno );

            m_isunprepready   = new boolean[ m_nreplicas ];
            m_prepwndendlocno = new long[ m_nreplicas ];
            clearAllProposerWindows();

            m_propinststep = useRotatingLeader() ? m_nreplicas : 1;
        }
        else
        {
            if( useRotatingLeader() )
                throw new NotImplementedException();

            HybsterNewView nvshard  = nv.getNewViewShards()[ m_shardno ];
            byte            proposer = (byte) nvshard.getSender();

            TrustedCounterValue curval = orderCertifier( proposer ).getCertifier().counterValue();

            if( curval.getHighValue()!=nv.getViewNumber() )
            {
                s_logger.debug( ViewLogging.MARKER, "{} forward order counter from {} to {}-0",
                                this, curval, nv.getViewNumber() );

                orderCertifier( proposer ).getCertifier().forwardCounterValue( nv.getViewNumber(), 0 );
            }

            m_orderno_last = 0;

            // New instances are already initialised with the new view. Nevertheless, for simplicity,
            // we initialise them again in the following.
            // TODO: Initial/empty checkpoint certificate
            if( nvshard.getCheckpointCertificate()!=null )
                initStableCheckpoint( nvshard.getCheckpointCertificate().getOrderNumber() );

            // Initialise instances with new view.
            long              current  = m_actinsts.getWindowStart();
            long              wndend   = m_actinsts.getWindowEnd();
            HybsterPrepare[] newpreps = nvshard.getNewPrepares();
            int               npidx    = 0;

            // TODO: Okay, that's something like a dirty hack. We need to set the PREPAREs somehow.
            //       A better solution: New prepares for views are created by order shards and not view change shards.
            // If we are the leader of this view, we send our PREPAREs with the NEW-VIEW. If we are not the leader
            // we have to process everything in-order as usual.
            // Note, m_prepwndendlocno is used by the following enqueueMessageToActiveInstance().
            long    lastprepno = proposer==m_repno ? nvshard.getMaximumOrderNumber() : nvshard.getMinimumOrderNumber();

            // FIXME: This is still broken. If we have a newer we would not need to go through the loop.
            //        However, currently we have to. Further, if there is only a checkpoint and no PREPAREs
            //        that perhaps must be handled differently. No time...
            // If a checkpoint got stable during the view change, we do not need to and must not go back.
            if( lastprepno<m_wndstart )
                m_prepwndendlocno[ proposer ] = m_actinsts.getWindowStart();
            else
                m_prepwndendlocno[ proposer ] = m_instdist.getSlotForUnit( m_shardno, lastprepno+1 );

            while( current<wndend )
            {
                HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( current );

                // We could check if the proposals are contained in the new view ...
                // or we simply reorder them.
                if( inst.isPrepared() && !inst.isCommitted() && inst.isProposer() )
                    inst.getCommand().forEach( this::enqueueCommand );

                initInstance( inst, current );

                if( npidx<newpreps.length && newpreps[ npidx ].getOrderNumber()==inst.getOrderNumber() )
                {
                    HybsterPrepare prepare = newpreps[ npidx++ ];

                    if( inst.isProposer() )
                        inst.setPrepare( prepare );
                    else
                        enqueueMessageToActiveInstance( inst, prepare );
                }

                current++;
            }

            // Insert messages already received for the new view.
            Collection<OrderNetworkMessage> msgs = m_futuremsgs.remove( m_curviewno );

            if( msgs!=null )
            {
                for( OrderNetworkMessage msg : msgs )
                    enqueueOrderMessage( msg );
            }

            // Clear messages of skipped views.
            // TODO: Log which messages are dropped that way.
            m_futuremsgs.keySet().removeIf( viewno -> viewno<=m_curviewno );

            if( s_logger.isDebugEnabled( ViewLogging.MARKER ) )
            {
                HybsterOrderInstance inst = m_actinsts.getSlot( m_prepwndendlocno[ proposer ] );

                s_logger.debug( ViewLogging.MARKER, "{} Next unprepared instance for proposer {}: {}/{} ({})",
                                this, proposer, inst.getLocalNumber(), inst.getOrderNumber(), inst.isReady() );
            }
        }

    }


    public void initStableCheckpoint(long chkptno)
    {
        // Only move window forward
        if( chkptno+1<=m_wndstart )
            return;

        long newwndstart      = chkptno+1;
        long newwndstartlocno = m_instdist.getSlotForUnit( m_shardno, newwndstart );

        // TODO: Extension points
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

        for( byte i=0; i<m_nreplicas; i++ )
        {
            if( m_prepwndendlocno[ i ]<newwndstartlocno )
                clearProposerWindow( i );
        }

        // If the new start of the window is above the end of the previous active window, instances are skipped.
        if( newwndstartlocno>m_backlog.getWindowStart() )
            m_backlog.skipSlots( newwndstartlocno );

        long current = m_actinsts.forwardWindow( newwndstartlocno );
        long wndend  = m_actinsts.getWindowEnd();

        if( !isCurrentViewStable() )
        {
            while( current<wndend )
                initInstance( current++ );
        }
        else
        {
            while( current<wndend )
            {
                HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( current );

                // TODO: Store this when sending messages or get the current value from the counter?
                //       + Replicas should start with a view change.
                if( chkptno>0 && inst.getOwnMessage()!=null )
                    m_orderno_last = inst.getOrderNumber();

                initInstance( inst, current );

                m_backlog.forwardWindow( inst::enqueueMessage );

                checkProposerWindow( inst );

                current++;
            }

            m_readypreps.removeTo( newwndstartlocno );

            if( s_logger.isDebugEnabled( CheckpointLogging.MARKER ) )
            {
                StringBuffer sb = new StringBuffer();

                sb.append( "[" );
                for( byte i=0; i<m_nreplicas; i++ )
                {
                    long nunprep = m_prepwndendlocno[ i ];

                    if( nunprep==Long.MAX_VALUE )
                        sb.append( "-" );
                    else
                    {
                        sb.append( nunprep );
                        sb.append( "/" );
                        sb.append( m_instdist.getUnitForSlot( m_shardno, nunprep ) );

                        if( nunprep<m_actinsts.getWindowEnd() )
                        {
                            sb.append( "(" ).append( m_actinsts.getSlot( nunprep ).isReady() ).append( ")" );
                        }
                    }
                    sb.append( ", " );
                }
                sb.delete( sb.length()-2, sb.length() );
                sb.append( "]" );

                s_logger.debug( CheckpointLogging.MARKER, "{} next unprepared {}", this, sb.toString() );
            }
        }
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
            enqueueOrderMessage( (OrderNetworkMessage) msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            enqueueCheckpointStable( (CheckpointStable) msg );
            break;
        default:
            enqueueCommand( (Command) msg );
            break;
        }
    }


    private void enqueueCommand(Command cmd)
    {
        m_proposals.add( cmd );

        if( isCurrentViewStable() )
        {
            if( m_repno!=m_proposer )
                m_isforwardready = true;
            else
                checkProposerReady( m_repno );
        }
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
        // TODO: Use a cleaner approach like an extension point.
        else if( isMessageForCurrentView( msg ) )
        {
            long locno = m_instdist.getSlotForLocalUnit( m_shardno, msg.getOrderNumber() );

            if( locno>=m_backlog.getWindowStart() )
                m_backlog.add( locno, msg );
            else
                enqueueMessageToActiveInstance( m_actinsts.getSlotUnchecked( locno ), msg );
        }
    }


    private boolean isMessageForCurrentView(OrderNetworkMessage msg)
    {
        if( forcedViewChangeInterval()==0 )
            return true;
        else
        {
            int expview = (int) ( msg.getOrderNumber()/forcedViewChangeInterval() ) + 1;

            // Are we in the view at which this message is supposed to be handled?
            if( m_curviewno>=expview )
                return true;
            else
            {
                // Only one shard requests the view change.
                if( msg.getOrderNumber() % forcedViewChangeInterval()==0 )
                {
                    try
                    {
                        Thread.sleep( forcedViewChangeTimeout() );
                    }
                    catch( InterruptedException e )
                    {
                        throw new IllegalStateException( e );
                    }

                    m_vwchghandler.requestViewChange();
                }

                // If not, drop the message.
                return false;
            }
        }
    }


    private void enqueueMessageToActiveInstance(HybsterOrderInstance inst, OrderNetworkMessage msg)
    {
        inst.enqueueMessage( msg );

        long localno  = inst.getLocalNumber();
        byte proposer = inst.getProposer();

        if( localno<m_prepwndendlocno[ proposer ] )
            m_readypreps.add( localno );
        else if( localno==m_prepwndendlocno[ proposer ] )
            checkProposerReady( proposer );
    }


    private void enqueueCheckpointStable(CheckpointStable msg)
    {
        m_enqueuedchkpt = Math.max( m_enqueuedchkpt, msg.getOrderNumber() );
    }


    @Override
    public boolean isReady()
    {
        return isAnyProposerReady() || !m_readypreps.isEmpty() || m_enqueuedchkpt!=Long.MIN_VALUE ||
               m_isforwardready;
    }


    @Override
    public boolean execute()
    {
        assert isReady();

        if( m_enqueuedchkpt!=Long.MIN_VALUE )
            processCheckpoint();

        if( !m_readypreps.isEmpty() )
            processPrepared();

        if( isAnyProposerReady() )
            processUnprepared();

        if( m_isforwardready )
            forwardProposals();

        return !isReady();
    }


    private void processUnprepared()
    {
        for( byte i=0; isAnyProposerReady() && i<m_nreplicas; i++ )
        {
            if( isProposerReady( i ) )
            {
                processUnprepared( i );
                clearProposerReady( i );
            }
        }

        if( s_logger.isDebugEnabled() && m_proposals.size()>0 )
            s_logger.debug( "{} no instances available to propose {} comands", this, m_proposals.size() );
    }


    private void processUnprepared(byte proposer)
    {
        assert isCurrentViewStable();

        while( m_prepwndendlocno[ proposer ]<m_actinsts.getWindowEnd() )
        {
            HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( m_prepwndendlocno[ proposer ] );

            assert inst.getProposer()==proposer : "Instance proposer " + inst.getProposer() + " vs. " + proposer;

            if( inst.isProposer() )
            {
                if( !isProposalReady() )
                    break;

                assert !inst.isReady() : inst;

                assignProposals( inst );

                assert inst.isPrepared();
            }
            else
            {
                if( !inst.isReady() )
                {
                    assert !inst.isPrepared() && !inst.isCommitted() : inst;
                    break;
                }

                assert !inst.isCommitted() : inst;

                inst.execute();

                if( inst.isCommitted() )
                    instanceCompleted( inst );
                else if( !inst.isPrepared() )
                    break;
            }

            m_prepwndendlocno[ proposer ] = nextLocalNumberForProposer( proposer, m_prepwndendlocno[ proposer ] );
        }
    }


    private void assignProposals(HybsterOrderInstance inst)
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


    private void processPrepared()
    {
        assert isCurrentViewStable();

        HybsterOrderInstance inst;

        while( ( inst = m_readypreps.poll() )!=null )
        {
            boolean iscom = inst.isCommitted();

            inst.execute();

            if( !iscom && inst.isCommitted() )
                instanceCompleted( inst );
        }
    }


    private void instanceCompleted(HybsterOrderInstance inst)
    {
        m_orderlearner.enqueueMessage( new CommandOrdered( inst.getProposer(), inst.getOrderNumber(), inst.getResult() ) );
    }


    private void processCheckpoint()
    {
        long chkptno = m_enqueuedchkpt;
        m_enqueuedchkpt = Long.MIN_VALUE;

        initStableCheckpoint( chkptno );
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
        // TODO: Process asynchronous with highest priority?
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
            abortView( m_vwchghandler.getViewNumber() );
        else
        {
            assert m_vwchghandler.isViewStable();

            initStableView( m_vwchghandler.getStableView() );
        }
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private HybsterOrderInstance initInstance(long localno)
    {
        HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( localno );

        initInstance( inst, localno );

        return inst;
    }


    private void initInstance(HybsterOrderInstance inst, long localno)
    {
        inst.init( localno, m_instdist.getUnitForSlot( m_shardno, localno ), m_stableview );
    }


    private boolean isAnyProposerReady()
    {
        return m_nreadyprops>0;
    }


    private boolean isProposerReady(byte proposer)
    {
        return m_isunprepready[ proposer ];
    }


    private void markProposerReady(byte proposer)
    {
        m_isunprepready[ proposer ] = true;
        m_nreadyprops++;
    }


    private void clearProposerReady(byte proposer)
    {
        m_isunprepready[ proposer ] = false;
        m_nreadyprops--;
    }


    private void checkProposerReady(byte proposer)
    {
        long nextprop = m_prepwndendlocno[ proposer ];

        if( !m_isunprepready[ proposer ] && nextprop<m_actinsts.getWindowEnd() &&
                isInstanceReady( m_actinsts.getSlotUnchecked( nextprop ) ) )
       {
            markProposerReady( proposer );
       }
    }


    private void checkProposerWindow(HybsterOrderInstance inst)
    {
        long localno  = inst.getLocalNumber();
        byte proposer = inst.getProposer();
        long nextprop = m_prepwndendlocno[ proposer ];

        if( localno<=nextprop )
        {
            if( localno<nextprop )
            {
                assert nextprop==Long.MAX_VALUE;

                m_prepwndendlocno[ proposer ] = localno;
            }

            if( isInstanceReady( inst ) )
                markProposerReady( proposer );
        }
    }


    private void clearAllProposerWindows()
    {
        m_nreadyprops = 0;
        Arrays.fill( m_isunprepready, false );
        Arrays.fill( m_prepwndendlocno, Long.MAX_VALUE );
    }


    private void clearProposerWindow(byte proposer)
    {
        m_prepwndendlocno[ proposer ] = Long.MAX_VALUE;

        if( m_isunprepready[ proposer ] )
            clearProposerReady( proposer );
    }


    private boolean isInstanceReady(HybsterOrderInstance inst)
    {
        return inst.isProposer() && !inst.isPrepared() ? isProposalReady() : inst.isReady();
    }


    private boolean isProposalReady()
    {
        return m_proposals.size()>=minumumBatchSize();
    }


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
    public TrustedCounterGroupCertifier getOrderCertifier(byte proposer)
    {
        return m_cntxt.getOrderCertifier( proposer );
    }


    @Override
    public MessageVerifier<? super Command> getProposalVerifier()
    {
        return m_cntxt.getProposalVerifier();
    }

}
