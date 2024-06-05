package refit.hybstero.order;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.hybstero.checkpoint.HybsterCheckpointMessages;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpoint;
import refit.hybstero.view.HybsterViewChangeHandler;
import refit.hybstero.view.HybsterViewChangeMessages.HybsterNewViewStable;
import reptor.chronos.Actor;
import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomainContext;
import reptor.chronos.PushMessageSink;
import reptor.distrbt.com.Message;
import reptor.jlib.collect.FixedSlidingCollectionWindow;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.jlib.collect.SlidingCollectionWindow;
import reptor.jlib.collect.UnboundSlidingCollectionWindow;
import reptor.jlib.collect.mark.RangeWindowMarker;
import reptor.jlib.collect.mark.SlidingWindowMarker;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.common.order.OrderExtensions;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.order.OrderMessages.CommandBatch;
import reptor.replct.agree.common.order.OrderMessages.CommandOrdered;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.ViewLogging;
import reptor.replct.common.InstanceDistribution;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Request;


public class HybsterOrderShard implements ProtocolHandler, MessageHandler<Message>,
                                          Actor, PushMessageSink<Message>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context extends HybsterOrderInstance.Context
    {
        ChronosDomainContext getDomainContext();

        MessageTransmitter   getReplicaTransmitter();

        InstanceDistribution getOrderInstanceShardDistribution();

        int                  getExecutorNumberForOrderShard();

        int                  getOrderWindowSize();
        int                  getOrderWindowSizeForShard();
        int                  getActiveOrderWindowSize();
        int                  getActiveOrderWindowSizeForShard();
        boolean              useUnboundOrderWindow();

        int                  getMinumumCommandBatchSize();
        int                  getMaximumCommandBatchSize();

        boolean              useAsynchrounousCheckpoints();
        int                  getCheckpointInterval();
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterOrderShard.class );

    private final Context              m_cntxt;
    private final short                m_shardno;
    private final InstanceDistribution m_instdist;

    private final HybsterViewChangeHandler m_vwchghandler;

    private long m_wndstart;
    private long m_wndend;
    private long m_prepwndendlocno; // (Exclusive) end of the window of prepared instances ->
                                    // first instance that is not yet prepared (according to the local sequence).
    private long m_nextchkpt = -1;

    private long    m_enqueuedchkptsent = Long.MIN_VALUE;
    private long    m_enqueuedchkpt     = Long.MIN_VALUE; // The first checkpoint has the order number -1.
    private boolean m_isforwardready    = false;
    private boolean m_isunprepready     = false;
    private final SlidingWindowMarker<HybsterOrderInstance> m_readypreps;

    private final Queue<Request>                                 m_proposals;
    private final FixedSlidingWindow<HybsterOrderInstance>       m_actinsts;
    private final SlidingCollectionWindow<OrderNetworkMessage>  m_backlog;
    private final Map<Integer, Collection<OrderNetworkMessage>> m_futuremsgs = new HashMap<>();

    private final boolean m_useunboundwnd;

    // Configuration dependent
    private PushMessageSink<Message> m_orderlearner;

    // View dependent
    private View m_stableview = null;
    private int  m_curviewno  = -1;
    private byte m_repno      = -1;


    public HybsterOrderShard(Context cntxt, short shardno, OrderExtensions extmanager)
    {
        // Configuration.
        m_cntxt   = cntxt;
        m_shardno = shardno;

        m_instdist      = cntxt.getOrderInstanceShardDistribution();
        m_useunboundwnd = cntxt.useUnboundOrderWindow();

        // Members and subjects
        m_wndstart        = -activeWindowSize();
        m_wndend          = useUnboundWindow() ? Long.MAX_VALUE : m_wndstart + windowSize();
        m_prepwndendlocno = 0;

        m_proposals  = new ArrayDeque<Request>();

        m_actinsts   = new FixedSlidingWindow<>( activeWindowSizeForShard(),
                                                 i -> new HybsterOrderInstance( cntxt, extmanager.getProtocolInstanceObserver( shardno, i ) ),
                                                 -activeWindowSizeForShard() );
        m_readypreps = new RangeWindowMarker<>( m_actinsts, t -> t.isReady() );

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


    private boolean useUnboundWindow()
    {
        return m_useunboundwnd;
    }

    private int windowSize()
    {
        return m_cntxt.getOrderWindowSize();
    }

    private int windowSizeForShard()
    {
        return m_cntxt.getOrderWindowSizeForShard();
    }

    private int activeWindowSize()
    {
        return m_cntxt.getActiveOrderWindowSize();
    }

    private int activeWindowSizeForShard()
    {
        return m_cntxt.getActiveOrderWindowSizeForShard();
    }

    private int minumumBatchSize()
    {
        return m_cntxt.getMinumumCommandBatchSize();
    }

    private int maximumBatchSize()
    {
        return m_cntxt.getMaximumCommandBatchSize();
    }

    private boolean asyncCheckpoints()
    {
        return m_cntxt.useAsynchrounousCheckpoints();
    }

    private int checkpointInterval()
    {
        return m_cntxt.getCheckpointInterval();
    }

    private int executor()
    {
        return m_cntxt.getExecutorNumberForOrderShard();
    }

    private MessageTransmitter replicaTransmitter()
    {
        return m_cntxt.getReplicaTransmitter();
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

    public void initPeers(AgreementPeers config)
    {
        m_orderlearner = config.getExecutors().get( executor() ).createChannel( domain() );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case InvocationMessages.REQUEST_ID:
            enqueueRequest( (Request) msg );
            break;
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
            enqueueOrderMessage( (OrderNetworkMessage) msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            enqueueCheckpointStable( (CheckpointStable) msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_CHECKPOINT_ID:
            enqueueCheckpointSent( (HybsterCheckpoint) msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }
    }


    private void enqueueRequest(Request request)
    {
        m_proposals.add( request );

        if( isCurrentViewStable() )
        {
//            if( m_repno!=m_stableview.getContactReplicaID() )
//                m_isforwardready = true;
//            else if( !m_isunprepready && m_prepwndendlocno<m_actinsts.getWindowEnd() )
//                m_isunprepready = isInstanceReady( m_actinsts.getSlotUnchecked( m_prepwndendlocno ) );
        }
    }


    private void enqueueOrderMessage(OrderNetworkMessage msg)
    {
        if( msg.getViewNumber()<m_curviewno || msg.getOrderNumber()<m_wndstart )
            s_logger.debug( "{} drop outdated message ", this, msg );
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
            long locno = m_instdist.getLocalSequenceForLocalInstance( m_shardno, msg.getOrderNumber() );

            if( locno>=m_backlog.getWindowStart() )
                m_backlog.add( locno, msg );
            else
            {
                HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( locno );
                inst.enqueueMessage( msg );

                if( locno<m_prepwndendlocno )
                    m_readypreps.add( locno );
                else if( locno==m_prepwndendlocno && !m_isunprepready )
                    m_isunprepready = isInstanceReady( inst );
            }
        }
    }


    private void enqueueCheckpointStable(CheckpointStable msg)
    {
        m_enqueuedchkpt = Math.max( m_enqueuedchkpt, msg.getOrderNumber() );
    }


    private void enqueueCheckpointSent(HybsterCheckpoint msg)
    {
        m_enqueuedchkptsent = Math.max( m_enqueuedchkptsent, msg.getOrderNumber() );
    }


    @Override
    public boolean isReady()
    {
        return m_isunprepready || !m_readypreps.isEmpty() || m_enqueuedchkpt!=Long.MIN_VALUE ||
               m_enqueuedchkptsent!=Long.MIN_VALUE || m_isforwardready;
    }


    @Override
    public boolean execute()
    {
        assert isReady();

        if( m_enqueuedchkpt!=Long.MIN_VALUE )
            processCheckpoint();

        if( m_enqueuedchkptsent!=Long.MIN_VALUE )
            processCheckpointSent();

        if( !m_readypreps.isEmpty() )
            processPrepared();

        if( m_isunprepready )
            processUnprepared();

        if( m_isforwardready )
            forwardProposals();

        return !isReady();
    }


    private void processUnprepared()
    {
        for( ; m_prepwndendlocno<m_actinsts.getWindowEnd(); m_prepwndendlocno++ )
        {
            HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( m_prepwndendlocno );

            if( inst.isProposer() )
            {
                if( !isProposalReady() )
                    break;

                assert !inst.isReady();

                assignProposals( inst );

                assert inst.isPrepared();
            }
            else
            {
                if( !inst.isReady() )
                {
                    assert !inst.isPrepared() && !inst.isCommitted();
                    break;
                }

                assert !inst.isCommitted();

                inst.execute();

                if( inst.isCommitted() )
                    instanceCompleted( inst );
                else if( !inst.isPrepared() )
                    break;
            }
        }

        m_isunprepready = false;

        if( s_logger.isDebugEnabled() && m_proposals.size()>0 )
            s_logger.debug( "{} no instances available to propose {} requests", this, m_proposals.size() );
    }


    private boolean isProposalReady()
    {
        return m_proposals.size()>=minumumBatchSize();
    }


    private void assignProposals(HybsterOrderInstance inst)
    {
        if( maximumBatchSize()==1 || ( m_proposals.size()==1 ) )
            inst.handleCommand( m_proposals.poll() );
        else
        {
            int       bs   = Math.min( m_proposals.size(), maximumBatchSize() );
            Request[] reqs = new Request[ bs ];

            for( int i=0; i<bs; i++ )
                reqs[ i ] = m_proposals.poll();

            CommandBatch batch = new CommandBatch( m_repno, inst.getOrderNumber(), m_curviewno, reqs );
            batch.setValid();
            batch.setCertificateSize( 0 );

            inst.handleCommand( batch );
        }
    }


    private void processPrepared()
    {
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

        long newwndstartlocno = m_instdist.getLocalSequenceForInstance( m_shardno, chkptno+1 );

        // Only move window forward
        if( newwndstartlocno<=m_actinsts.getWindowStart() )
            return;

        s_logger.debug( "{} move window from {} to {}", this, m_actinsts.getWindowStart(), newwndstartlocno );

        m_wndstart = chkptno+1;
        if( !useUnboundWindow() )
            m_wndend = m_wndstart + windowSize();
        m_prepwndendlocno = Math.max( m_prepwndendlocno, newwndstartlocno );

        // If the new start of the window is above the end of the previous active window, instances are skipped.
        if( newwndstartlocno>m_backlog.getWindowStart() )
            m_backlog.skipSlots( newwndstartlocno );

        long current = m_actinsts.forwardWindow( newwndstartlocno );
        long wndend  = m_actinsts.getWindowEnd();

        while( current<wndend )
        {
            HybsterOrderInstance inst = m_actinsts.getSlotUnchecked( current );

            inst.init( m_stableview, m_instdist.getInstanceForLocalSequence( m_shardno, current ) );
            m_backlog.forwardWindow( inst::enqueueMessage );

            current++;
        }

        unblockNextInterval( chkptno );

        m_readypreps.removeTo( newwndstartlocno );
        m_isunprepready = isInstanceReady( m_actinsts.getSlotUnchecked( m_prepwndendlocno ) );
    }


    private void processCheckpointSent()
    {
        long chkptno = m_enqueuedchkptsent;
        m_enqueuedchkptsent = Long.MIN_VALUE;

        if( unblockNextInterval( chkptno ) )
            m_isunprepready = isInstanceReady( m_actinsts.getSlotUnchecked( m_prepwndendlocno ) );
    }


    private boolean unblockNextInterval(long chkptno)
    {
        if( chkptno<m_nextchkpt )
            return false;
        else
        {
            m_nextchkpt = chkptno + checkpointInterval();

            return true;
        }
    }


    private void forwardProposals()
    {
//        for( Request request : m_proposals )
//            replicaTransmitter().unicastMessage( request, m_stableview.getContactReplicaID() );

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


    public void initStableView(HybsterNewViewStable nv)
    {
        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, nv.getViewNumber() );

        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();

        // The first view change is taken as a change of the group composition.
        if( m_curviewno==0 )
            m_repno = m_stableview.getReplicaGroup().getReplicaNumber();

        // TODO: m_prewndendlocno, m_isforwardready, m_isunprepready?

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

    private boolean isInstanceReady(HybsterOrderInstance inst)
    {
        if( !asyncCheckpoints() && inst.getOrderNumber()>m_nextchkpt )
            return false;

        return inst.isProposer() ? isProposalReady() : inst.isReady();
    }


    private boolean isCurrentViewStable()
    {
        // TODO: Use initial object (-1)?
        return m_stableview!=null && m_curviewno==m_stableview.getNumber();
    }

}
