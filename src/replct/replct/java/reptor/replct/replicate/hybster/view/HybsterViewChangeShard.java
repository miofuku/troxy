package reptor.replct.replicate.hybster.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.distrbt.certify.trusted.CounterCertifier;
import reptor.distrbt.certify.trusted.TrustedCounterGroupCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.jlib.NotImplementedException;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolShard;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.order.OrderMessages.Noop;
import reptor.replct.agree.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.ViewChangeNetworkMessage;
import reptor.replct.common.WorkDistribution;
import reptor.replct.replicate.hybster.order.HybsterOrdering;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterPrepare;
import reptor.replct.replicate.hybster.view.HybsterLocalNewViewMessages.HybsterNewViewReady;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewView;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterViewChange;


public class HybsterViewChangeShard implements ProtocolShard, MessageHandler<Message>,
                                                LocalViewChangePreparationProtocol.Context,
                                                HybsterLocalNewViewProtocol.Context,
                                                HybsterLocalStableViewProtocol.Context
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context
    {
        ChronosDomainContext    getDomainContext();

        MessageMapper                                       getMessageMapper();

        ReplicaPeerGroup                           getPeerGroup();

        PushMessageSink<? super ViewChangeNetworkMessage>   getReplicaChannel();
        TrustedCounterGroupCertifier                        getOrderCertifier(byte proposer);
        TrustedCounterGroupCertifier                        getNewViewCertifier();

        HybsterOrdering    getOrderingProtocol();
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterViewChangeShard.class );

    private final Context  m_cntxt;
    private final short    m_shardno;

    private final Noop     m_noop;

    private final boolean  m_isintcoord;

    private final HybsterLocalViewChangePreparationHandler m_locvc;
    private final HybsterLocalNewViewProtocol.Handler      m_locnv;
    private final HybsterLocalStableViewProtocol.Handler   m_locsv;

    private View m_groupview = null;
    private byte m_repno     = -1;

    private PushMessageSink<Message> m_vcacceptors = null; // All order, checkpoint, and view change shards
    private PushMessageSink<Message> m_vcshards    = null;
    private PushMessageSink<Message> m_vccoord     = null;
    private PushMessageSink<Message> m_vclearners  = null;


    public HybsterViewChangeShard(Context cntxt, short shardno, IntFunction<View> viewfac)
    {
        m_cntxt      = cntxt;
        m_shardno    = shardno;

        m_isintcoord  = shardno==0;

        m_locvc       = new HybsterLocalViewChangePreparationHandler( this );
        m_locnv       = new HybsterLocalNewViewProtocol.Handler( this );
        m_locsv       = HybsterLocalStableViewProtocol.Handler.createForAcceptor( this, viewfac );

        m_noop = new Noop();
        cntxt.getMessageMapper().serializeMessage( m_noop );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainContext().getDomainAddress();
    }


    private MessageMapper mapper()
    {
        return m_cntxt.getMessageMapper();
    }

    private PushMessageSink<? super ViewChangeNetworkMessage> replicaChannel()
    {
        return m_cntxt.getReplicaChannel();
    }

    // TODO: Should be exclusive to the order shard.
    private TrustedCounterGroupCertifier orderCertifier(byte proposer)
    {
        return m_cntxt.getOrderCertifier( proposer );
    }

    private TrustedCounterGroupCertifier newViewCertifier()
    {
        return m_cntxt.getNewViewCertifier();
    }

    private boolean useRotatingLeader()
    {
        return m_cntxt.getOrderingProtocol().getUseRotatingLeader();
    }

    private WorkDistribution orderInstanceDistribution()
    {
        return m_cntxt.getOrderingProtocol().getOrderInstanceShardDistribution();
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "VWC%02d", m_shardno );
    }


    @Override
    public short getShardNumber()
    {
        return m_shardno;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initPeers(AgreementPeers peers)
    {
        m_vccoord = peers.getViewChangeShards().get( peers.getInternalViewChangeCoordinator( 0 ) ).createChannel( domain() );

        Set<DomainEndpoint<PushMessageSink<Message>>> recset = new HashSet<>();
        recset.addAll( peers.getOrderShards() );
        recset.addAll( peers.getCheckpointShards() );
        recset.addAll( peers.getViewChangeShards() );
        recset.addAll( peers.getClientShards() );
        m_vclearners = peers.createMulticastChannel( recset, domain() );

        if( m_isintcoord )
        {
            m_vcshards    = peers.createMulticastChannel( peers.getViewChangeShards(), domain() );

            recset.clear();
            recset.addAll( peers.getOrderShards() );
            recset.addAll( peers.getCheckpointShards() );
            recset.addAll( peers.getViewChangeShards() );
            m_vcacceptors = peers.createMulticastChannel( recset, domain() );
        }
        else
        {
            m_vcshards    = null;
            m_vcacceptors = null;
        }

        m_locvc.initPeers( peers );
        m_locnv.initPeers( peers );
        m_locsv.initPeers( peers );
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case ViewChangeMessages.REQUEST_VIEW_CHANGE_ID:
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
        case ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_READY_ID:
            handleLocalViewChange( msg );
            break;
        case HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID:
        case ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID:
        case ViewChangeMessages.NEW_VIEW_READY_ID:
            handleLocalNewView( msg );
            break;
        case HybsterViewChangeMessages.HYBSTER_NEW_VIEW_ID:
        case ViewChangeMessages.CONFIRM_NEW_VIEW_ID:
        case ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleLocalStableView( msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }

        return false;
    }


    private void handleLocalViewChange(Message msg)
    {
        if( !m_locvc.handleMessage( msg ) )
            return;

        if( m_locvc.isViewChangeInitiated() )
            m_locvc.prepareViewChangeShard();
        else if( m_locvc.isViewChangeReady() )
            viewChangeReady();
    }


    private void viewChangeReady()
    {
        if( useRotatingLeader() )
            throw new NotImplementedException();

        HybsterViewChange vc = HybsterViewChange.createFromStableView( m_repno, m_locvc.getViewNumber(), m_shardno, m_locvc.getCheckpointCertificate(),
                                                                         m_locvc.getPreparedSet(), m_locvc.getNumberOfLastActiveInstance() );
        vc.setValid();

        s_logger.debug( "{} send view change {}", this, vc );

        CounterCertifier ordercertif = orderCertifier( (byte) 0 ).getCertifier();
        ordercertif.createContinuing().values( vc.getViewNumber(), 0, 0, 0 );

        mapper().certifyAndSerializeMessage( vc, ordercertif );
        replicaChannel().enqueueMessage( vc );

        handleLocalNewView( vc );
    }


    private void handleLocalNewView(Message msg)
    {
        if( !m_locnv.handleMessage( msg ) )
            return;

        if( m_locnv.isNewViewReady() )
        {
            newViewReady();
        }
        else if( m_locnv.isViewChangeAgreed() )
        {
            // TODO: Start timer on (group) followers.
        }
        else if( m_locnv.isViewChangeConfirmed() )
        {
            // TODO: "Second, if a replica receives a set of ￼f+1 valid view-change messages from other replicas
            //       for views greater than its current view, it sends a view-change message for the smallest view in the set"
            //       -> m_vcinit.initiateViewChange()
        }
    }


    private void newViewReady()
    {
        if( useRotatingLeader() )
            throw new NotImplementedException();

        // TODO: We need our own view change.
        HybsterNewViewReady nvred = m_locnv.getNewView();

        // Retrieve all view change messages this shard is responsible for.
        // Fortunately, we still assume a symmetric configuration...
        HybsterViewChange[] nvproof = new HybsterViewChange[ nvred.getNewViewProofs().length ];
        for( int i=0; i<nvproof.length; i++ )
            nvproof[ i ] = nvred.getNewViewProofs()[ i ][ m_shardno ];

        HybsterPrepare[] newpreps = createNewPrepares( nvproof, nvred.getMinS(), nvred.getMaxS() );

        HybsterNewView nv = new HybsterNewView( m_repno, nvproof, newpreps );
        nv.setValid();

        s_logger.debug( "{} send new view {}", this, nv );

        CounterCertifier nvcertif = newViewCertifier().getCertifier();
        nvcertif.createIndependent().value( nv.getViewNumber() );

        mapper().certifyAndSerializeMessage( nv, nvcertif );
        replicaChannel().equals( nv );

        handleLocalStableView( nv );
    }


    private void handleLocalStableView(Message msg)
    {
        if( !m_locsv.handleMessage( msg ) )
            return;

        View view = m_locsv.getNewView().getView();

        if( m_groupview==null )
        {
            m_groupview = view;
            m_repno     = view.getReplicaGroup().getReplicaNumber();
        }

        m_locvc.initStableView( view );
        m_locnv.initStableView( view );
        m_locsv.initStableView( view );
    }


    // TODO: Only the order shard should be responsible for creating order messages.
    private HybsterPrepare[] createNewPrepares(HybsterViewChange[] nvproof, long minorderno, long maxorderno)
    {
        // Okay, let's create the PREPARES for the new view. All prepare certificates in nvproof are ordered,
        // thus we can use a merge.
        ArrayList<HybsterPrepare> newpreps = new ArrayList<>();
        int   viewno  = nvproof[ 0 ].getViewNumber();

        // TODO: Usually [min,max) but here (min, max] (from mins and maxs) -> adapt somehow.
        // Some view change may contain older checkpoints. We have to find the first position that is greater
        // than the minimum order instance.
        // TODO: Encapsulate the merge.
        int[] curidxs = new int[ nvproof.length ];

        for( int i=0; i<nvproof.length; i++ )
        {
            OrderNetworkMessage[] prepset = nvproof[ i ].getPrepareMessages();

            int prepidx = 0;

            while( prepidx<prepset.length && prepset[ prepidx ].getOrderNumber()<=minorderno )
                prepidx++;

            curidxs[ i ] = prepidx;
        }

        WorkDistribution instdist = orderInstanceDistribution();
        long curlocalno = instdist.getSlotForUnit( m_shardno, minorderno+1 );
        long curorderno = instdist.getUnitForSlot( m_shardno, curlocalno );

        while( curorderno<=maxorderno )
        {
            // Find the smallest order number among the remaining prepare certificates.
            HybsterPrepare nextprep = null;

            for( int i=0; i<nvproof.length; i++ )
            {
                OrderNetworkMessage[] prepset = nvproof[ i ].getPrepareMessages();
                int prepidx = curidxs[ i ];

                if( prepidx<prepset.length && ( nextprep==null || prepset[ prepidx ].getOrderNumber()<nextprep.getOrderNumber() ) )
                    nextprep = (HybsterPrepare) prepset[ prepidx ];
            }

            // Fill the gap with noops if necessary.
            long nextprepno = nextprep!=null ? nextprep.getOrderNumber() : maxorderno+1;

            while( curorderno<nextprepno )
            {
                HybsterPrepare newprep = new HybsterPrepare( m_repno, curorderno, viewno, m_noop );
                newprep.setValid();

                certifyOrderMessage( newprep, m_repno );

                newpreps.add( newprep );

                curorderno = instdist.getUnitForSlot( m_shardno, ++curlocalno );
            }

            // Apparently, we reached the end at all view change messages.
            if( nextprep==null )
                break;

            assert curorderno==nextprep.getOrderNumber();

            // Create the next PREPARE
            HybsterPrepare newprep = new HybsterPrepare( m_repno, curorderno, viewno, nextprep.getCommand() );
            newprep.setValid();

            certifyOrderMessage( newprep, m_repno );

            newpreps.add( newprep );

            // Increase the indices for all VIEW-CHANGEs that have a certificate for this order number.
            for( int i=0; i<nvproof.length; i++ )
            {
                OrderNetworkMessage[] prepmsgs = nvproof[ i ].getPrepareMessages();
                int idx = curidxs[ i ];

                if( idx<prepmsgs.length && ( prepmsgs[ idx ].getOrderNumber()==curorderno ) )
                    curidxs[ i ]++;
            }

            curorderno = instdist.getUnitForSlot( m_shardno, ++curlocalno );
        }

        if( s_logger.isDebugEnabled() )
        {
            s_logger.debug( "{} transferred instances for view {} ({}-{})", this, viewno, minorderno, maxorderno );

//            for( HybsterXPrepare prep : newpreps )
//                Logger.logView( this, "    " + prep );
        }

        return newpreps.toArray( new HybsterPrepare[ 0 ] );
    }


    private void certifyOrderMessage(OrderNetworkMessage msg, byte proposer)
    {
        CounterCertifier ordercertif = orderCertifier( proposer ).getCertifier();

        ordercertif.createIndependent().value( msg.getViewNumber(), msg.getOrderNumber() );
        mapper().certifyAndSerializeMessage( msg, ordercertif );
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public boolean isInternalCoordinator()
    {
        return m_isintcoord;
    }

    @Override
    public MessageMapper getMessageMapper()
    {
        return m_cntxt.getMessageMapper();
    }

    @Override
    public ReplicaPeerGroup getReplicaGroupConfiguration()
    {
        return m_cntxt.getPeerGroup();
    }

    @Override
    public void verifyViewChangeMessage(ViewChangeNetworkMessage msg)
    {
        if( useRotatingLeader() )
            throw new UnsupportedOperationException();

        if( msg.getTypeID()==HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID )
        {
            HybsterViewChange vc = (HybsterViewChange) msg;

            CounterCertifier ordercertif = orderCertifier( (byte) 0 ).getVerifier( vc.getSender() );
            ordercertif.verifyContinuing().values( vc.getViewNumber(), 0, vc.getNumberOfLastView(), vc.getNumberOfLastActiveInstance() );

            mapper().verifyMessage( vc, ordercertif );
        }
        else
        {
            HybsterNewView nv = (HybsterNewView) msg;

            CounterCertifier ordercertif = newViewCertifier().getVerifier( nv.getSender() );
            ordercertif.verifyIndependent().value( nv.getViewNumber() );

            mapper().verifyMessage( nv, ordercertif );
        }
    }

    @Override
    public void enqueueForViewChangeCoordinator(Message msg)
    {
        m_vccoord.enqueueMessage( msg );
    }

    @Override
    public void enqueueForViewChangeAcceptors(Message msg)
    {
        m_vcacceptors.enqueueMessage( msg );
    }

    @Override
    public void enqueueForViewChangeLearners(Message msg)
    {
        m_vcshards.enqueueMessage( msg );
    }

    @Override
    public void enqueueForNewViewAcceptors(Message msg)
    {
        m_vcshards.enqueueMessage( msg );
    }

    @Override
    public void enqueueForNewViewLearners(Message msg)
    {
        m_vcshards.enqueueMessage( msg );
    }

    @Override
    public void enqueueForStableViewAcceptors(Message msg)
    {
        m_vcshards.enqueueMessage( msg );
    }

    @Override
    public void enqueueForStableViewLearners(Message msg)
    {
        m_vclearners.enqueueMessage( msg );
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

}
