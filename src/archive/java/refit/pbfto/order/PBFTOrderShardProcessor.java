package refit.pbfto.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.pbfto.suite.PBFT;
import refit.pbfto.view.PBFTViewChangeHandler;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewViewStable;
import refit.protocols.statetransfer.StateTransferProtocol.StateTransferHandler;
import reptor.chronos.ChronosTask;
import reptor.chronos.SchedulerContext;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.SlidingCollectionWindow;
import reptor.replct.MessageCategoryID;
import reptor.replct.MessageHandler;
import reptor.replct.MessageTypeID;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.common.order.OrderExtensions;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.ViewLogging;
import reptor.replct.common.InstanceDistribution;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.invoke.InvocationMessages.Request;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTOrderShardProcessor extends ProtocolShardModule implements PBFTOrderShardContext, MessageHandler<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTOrderShardProcessor.class );

    private final SlidingCollectionWindow<OrderNetworkMessage>  m_backlog;
    private final Map<Integer, Collection<OrderNetworkMessage>> m_futuremsgs = new HashMap<>();

    private final StateTransferHandler           m_statetrans;
    private final PBFTViewChangeHandler          m_vchandler;
    private final MessageMapper                  m_formatter;

    // View dependent
    private View m_stableview = null;
    private int  m_curviewno  = -1;
    private byte m_repno      = -1;


    public PBFTOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, PBFT repprot, ReplicaPeerGroup grpconf, OrderExtensions extmanager, MulticastChannel<? super NetworkMessage> reptrans)
    {
        // Initialize data structures
        super( master, no );
//        this.readySlots = new TreeSet<PBFTOrderSlot>();
//        this.proposals = new LinkedList<Request>();
//        this.m_instdist = Config.ORDER_INSTANCE_DISTRIBUTION;
//        m_formatter       = repprot.getMapperFactory().get();
//        this.m_reptrans   = new MessageTransmitter( m_formatter, null, null, reptrans );
//       this.m_ordershard = new PBFTOrderShard( repprot, no, grpconf, this, reptrans );
//
//        // Subjects
//        m_statetrans   = new StateTransferHandler( m_reptrans );
//        m_vchandler    = new PBFTViewChangeHandler( master.getDomainContext(), no );
//
//        // Create protocol-instance slots
//        m_wndstart      = -Config.ACTIVE_ORDER_WINDOW;
//        m_wndend        = Config.UNBOUND_ORDER_WINDOW ? Long.MAX_VALUE : m_wndstart + Config.ORDER_INSTANCES_WINDOW;
//        m_wndstartlocno = -Config.ACTIVE_ORDER_WINDOW_STAGE;
//        m_nextproplocno = 0;
//
//        m_slots = new PBFTOrderSlot[Config.ACTIVE_ORDER_WINDOW_STAGE];
//        Arrays.setAll( m_slots, i -> new PBFTOrderSlot( this, m_ordershard, i, extmanager ) );
//
//        int blsize = Config.ORDER_INSTANCES_WINDOW_STAGE-Config.ACTIVE_ORDER_WINDOW_STAGE;
//        if( Config.UNBOUND_ORDER_WINDOW )
//            m_backlog = new UnboundSlidingCollectionWindow<OrderNetworkMessage>( blsize );
//        else
//            m_backlog = new FixedSlidingCollectionWindow<OrderNetworkMessage>( blsize );
        throw new  NotImplementedException();
    }


    @Override
    public String toString()
    {
        return String.format( "ODR%02d", m_shardno );
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        if( MessageTypeID.category( msg.getTypeID() )==MessageCategoryID.ORDER )
        {
            handleOrderMessage( (OrderNetworkMessage) msg );
        }
        else
        {
            switch( msg.getTypeID() )
            {
//            case InternalReconfigurationProtocol.INTERNAL_NEW_CONFIGURATION_ID:
//                initConfig( (ReplicaConfiguration) ((InternalNewConfiguration) msg).getConfiguration() );
//                break;
            case CheckpointMessages.CHECKPOINT_STABLE_ID:
                CheckpointStable notification = (CheckpointStable) msg;
                moveWindow( notification.getOrderNumber() + 1 );
                break;
    //        case PANIC_NOTIFICATION:
    //            Logger.logEvent( this, "protocol abort" );
    //            abortCurrentInstances();
    //            break;
            case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
            case ViewChangeMessages.NEW_VIEW_STABLE_ID:
                handleViewChange( msg );
                break;
            default:
                handleProposal( (NetworkMessage) msg );
            }
        }

        return false;
    }


    @Override
    public void initPeers(AgreementPeers config)
    {
//        PushMessageSink<Message> orderlearner = config.getExecutionShards().get( Config.ORDERSTAGE.getExecutionStageID( m_shardno ) ).createChannel( domainAddress() );
//
//        for( PBFTOrderSlot slot : m_slots )
//            slot.initConfig( config, orderlearner );
//
//        m_vchandler.initConfig( config );
        throw new  NotImplementedException();
    }


    private void handleOrderMessage(OrderNetworkMessage msg)
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
            long locno = m_instdist.getLocalSequenceForLocalInstance( m_shardno, msg.getOrderNumber() );

            if( locno >= m_wndstartlocno+m_slots.length )
                m_backlog.add( locno, msg );
            else
                dispatchOrderMessage( msg );
        }
    }


    private boolean isInWindow(OrderNetworkMessage msg)
    {
        return msg.getOrderNumber()>=m_wndstart && msg.getOrderNumber()<m_wndend;
    }


    private void dispatchOrderMessage(OrderNetworkMessage msg)
    {
        // Forward message to the corresponding slot
        PBFTOrderSlot slot = m_slots[getSlotIndex( msg.getOrderNumber() )];

//        OutboundChannelEndpoint worker;

//        if( msg.isCertificateValid() == null && Config.WORKERSTAGE.useForVerification() &&
//                (worker = slot.getWorker( slot.generateMessageSalt() )) != null )
//        {
//            // TODO: Actually it would depend on the protocol instance for this message,
//            // which verification scheme has to be used for it.
//            worker.createChannel( domainAddress() ).enqueueMessage( new VerifyReplicaMessage( msg, this ) );
//        }
//        else
//        {
////            int nview = (int) (msg.getInstanceID() / 2500);
////            if( nview==1 && m_curviewno!=nview && ( msg.getInstanceID() % 2500 )==0 )
////                m_vchandler.requestViewChange();
//
//            boolean ready = slot.insertMessage( msg );
//
//            if( ready )
//                readySlots.add( slot );
//        }
        throw new  NotImplementedException();
    }


    private void handleViewChange(Message msg)
    {
        if( !m_vchandler.handleMessage( msg ) )
            return;

        if( m_vchandler.isViewChangeInitiated() )
        {
            m_curviewno  = m_vchandler.getViewNumber();
            m_stableview = null;

            m_vchandler.prepareOrderShard( m_ordershard.abortView( new OrderInstanceIterator() ) );
        }
        else
        {
            assert m_vchandler.isViewStable();

            initStableView( m_vchandler.getStableView() );
        }
    }


    private void initStableView(PBFTNewViewStable nv)
    {
        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();
        m_repno      = nv.getView().getReplicaGroup().getReplicaNumber();

        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, nv.getViewNumber() );

        // Reset next proposer seqence
        m_nextproplocno = m_wndstartlocno;

        // Clear backlog
        m_backlog.clear();

        // Init instances for the new view
        if( m_stableview.getNumber()!=0 )
            m_ordershard.initView( new OrderInstanceIterator(), nv );

        // Insert message stored for the new view.
        Collection<OrderNetworkMessage> msgs = m_futuremsgs.remove( m_curviewno );

        if( msgs!=null )
        {
            for( OrderNetworkMessage m : msgs )
                if( isInWindow( m ) )
                    dispatchOrderMessage( m );
        }

        m_futuremsgs.keySet().removeIf( viewno -> viewno<=m_curviewno );

        // Transfer proposals to new context.
//        if( m_repno!=m_stableview.getContactReplicaID() && proposals.size()>0 )
//        {
//            m_statetrans.transferMessages( m_stableview.getContactReplicaID(), new ArrayList<>( proposals ) );
//            proposals.clear();
//        }
    }


    private boolean isCurrentViewStable()
    {
        return m_stableview!=null && m_curviewno==m_stableview.getNumber();
    }


    private class OrderInstanceIterator implements Iterator<OrderInstance>
    {
        private final PBFTOrderSlot[] slots;
        private final long end;

        private long current;

        public OrderInstanceIterator()
        {
                slots   = PBFTOrderShardProcessor.this.m_slots;
                current = PBFTOrderShardProcessor.this.m_wndstartlocno;
                end     = current + slots.length;
        }

        @Override
        public boolean hasNext()
        {
            return current < end;
        }

        @Override
        public OrderInstance next()
        {
            if( !hasNext() )
                throw new NoSuchElementException();
            else
                return slots[ (int) ( current++ % slots.length ) ].getCurrentInstance();
        }
    }


    // ###########################
    // # PROTOCOL-INSTANCE SLOTS #
    // ###########################

    private final PBFTOrderSlot[]      m_slots;
    private final InstanceDistribution m_instdist;
    private final MessageTransmitter   m_reptrans;
    private final PBFTOrderShard       m_ordershard;

    private long m_wndstart;
    private long m_wndend;
    private long                       m_wndstartlocno;


    private int getSlotIndex(long instid)
    {
        return (int) (m_instdist.getLocalSequenceForLocalInstance( m_shardno, instid ) % m_slots.length);
    }


    private void moveWindow(long newWindowStartID)
    {
        long newWindowStartLocSeq = m_instdist.getLocalSequenceForInstance( m_shardno, newWindowStartID );

        // Only move window forward
        if( newWindowStartLocSeq <= m_wndstartlocno )
            return;

        s_logger.debug( "{} move window from {} to {}", this, m_wndstartlocno, newWindowStartLocSeq );

        // Calculate which slots to initialize
        long locSeq = m_wndstartlocno + m_slots.length;
        long endLocSeq = newWindowStartLocSeq + m_slots.length;

        // If the new start of the window is above the end of the current active window, instances are skipped.
        if( locSeq<newWindowStartLocSeq )
        {
            m_backlog.skipSlots( newWindowStartLocSeq );
            locSeq = newWindowStartLocSeq;
        }

        // InstanceDistribution.Iterator instIter = instdist.getInstanceIterator( id, locSeq );

        for( ; locSeq != endLocSeq; locSeq++ )
        {
            PBFTOrderSlot slot = m_slots[(int) (locSeq % m_slots.length)];
            readySlots.remove( slot );

            long instanceID = m_instdist.getInstanceForLocalSequence( m_shardno, locSeq );
            // long instanceID = instIter.nextInstance();
            boolean ready = slot.init( m_stableview, instanceID );

            if( m_backlog.forwardWindow( slot::insertMessage ) || ready )
                readySlots.add( slot );
        }

        // Update window information
//        m_wndstart       = newWindowStartID;
//        if( !Config.UNBOUND_ORDER_WINDOW )
//            m_wndend = m_wndstart + Config.ORDER_INSTANCES_WINDOW;
//        m_wndstartlocno = newWindowStartLocSeq;
//        m_nextproplocno = Math.max( m_nextproplocno, newWindowStartLocSeq );
        throw new  NotImplementedException();
    }


    protected void slotReady(PBFTOrderSlot slot)
    {
        readySlots.add( slot );
    }


    // ################################
    // # PROTOCOL-INSTANCE INVOCATION #
    // ################################

    private final Set<PBFTOrderSlot> readySlots;


    @Override
    protected void executeSubjects()
    {
        if( !isCurrentViewStable() )
            return;

        // Assign proposals to idle proposers
        assignProposals();

        // Invoke ready protocol instances in the order of their IDs (-> use of TreeSet)
        for( PBFTOrderSlot readySlot : readySlots )
            readySlot.execute();

        // Reset ready slots
        readySlots.clear();
    }


    // #############
    // # PROPOSALS #
    // #############

    // TODO: REFITMessage for proposals, REFITRequests for batches
    private final Queue<Request> proposals;
    private long                 m_nextproplocno;


    @Override
    public void enqueueProposal(NetworkMessage msg)
    {
        enqueueMessage( msg );
    }


    private void handleProposal(NetworkMessage proposal)
    {
//        if( isCurrentViewStable() && m_repno!=m_stableview.getContactReplicaID() )
//            m_statetrans.transferMessage( m_stableview.getContactReplicaID(), proposal );
//        else
//            proposals.add( (Request) proposal );
    }


    private void assignProposals()
    {
        // Return if there are no proposals to assign
        int nrOfProposals = proposals.size();
        if( nrOfProposals == 0 )
            return;

        // Return if the minimal batch size has not yet been reached
//        if( Config.ENABLE_BATCHING )
//        {
//            if( nrOfProposals < Config.MINIMUM_BATCH_SIZE )
//                return;
//        }
//
//        // Find idle proposer instances
//        long endLocSeq = m_wndstartlocno + m_slots.length;
//
//        while( nrOfProposals != 0 && m_nextproplocno != endLocSeq )
//        {
//            // Ignore slot if its current instance is not a proposer
//            PBFTOrderSlot slot = m_slots[(int) (m_nextproplocno++ % m_slots.length)];
//
//            if( slot.isProposer() )
//            {
//                // Mark slot as ready
//                readySlots.add( slot );
//
//                // Update proposer selection information
//                nrOfProposals = Config.ENABLE_BATCHING ? 0 : nrOfProposals - 1;
//            }
//        }
        throw new  NotImplementedException();
    }


    public OrderMessages.CommandContainer fetchProposal(long instanceID)
    {
//        if( !Config.ENABLE_BATCHING || ( proposals.size()==1 ) )
//            return proposals.poll();
//        else
//        {
//            int       bs   = Math.min( proposals.size(), Config.MAXIMUM_BATCH_SIZE );
//            Request[] reqs = new Request[ bs ];
//
//            for( int i=0; i<bs; i++ )
//                reqs[ i ] = proposals.poll();
//
//            CommandBatch batch = new CommandBatch( m_repno, instanceID, m_curviewno, reqs );
//            batch.setValid();
//            batch.setCertificateSize( 0 );
//
//            return batch;
//        }
        throw new  NotImplementedException();
    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }
}
