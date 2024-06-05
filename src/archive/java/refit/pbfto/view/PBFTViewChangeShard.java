package refit.pbfto.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.common.stores.ActiveMessageQueue;
import refit.common.stores.ActiveMessageStore;
import refit.pbfto.PBFTProtocolShard;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import refit.pbfto.suite.PBFT;
import refit.pbfto.view.PBFTLocalNewViewMessages.PBFTNewViewReady;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewView;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTViewChange;
import reptor.chronos.Actor;
import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomainContext;
import reptor.chronos.DomainEndpoint;
import reptor.chronos.PushMessageSink;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.MessageHandler;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTViewChangeShard extends    PBFTProtocolShard
                                 implements MessageHandler<Message>,
                                            LocalViewChangePreparationProtocol.Context,
                                            PBFTLocalNewViewProtocol.Context,
                                            PBFTLocalStableViewProtocol.Context,
                                            Actor, PushMessageSink<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTViewChangeShard.class );

    private final ChronosDomainContext m_cntxt;

    private final boolean m_isintcoord;

    private final PBFTLocalViewChangePreparationHandler m_locvc;
    private final PBFTLocalNewViewProtocol.Handler      m_locnv;
    private final PBFTLocalStableViewProtocol.Handler   m_locsv;
    private final MessageTransmitter                    m_strongtrans;

    private final ActiveMessageStore<Message> m_msgstore;

    private View m_groupview = null;
    private byte m_locrepno  = -1;

    private PushMessageSink<Message> m_vcacceptors = null; // All order, checkpoint, and view change shards
    private PushMessageSink<Message> m_vcshards    = null;
    private PushMessageSink<Message> m_vccoord     = null;
    private PushMessageSink<Message> m_vclearners  = null;


    public PBFTViewChangeShard(ChronosDomainContext cntxt, PBFT repprot, short shardno, MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( repprot, shardno, reptrans );

        m_cntxt       = cntxt;
        m_isintcoord  = shardno==0;

        m_msgstore    = new ActiveMessageQueue<>( this );

        m_strongtrans = new MessageTransmitter( m_mapper, m_strcon.getCertifier(), reptrans );
        m_locvc       = new PBFTLocalViewChangePreparationHandler( this );
        m_locnv       = new PBFTLocalNewViewProtocol.Handler( this );
        m_locsv       = PBFTLocalStableViewProtocol.Handler.createForAcceptor( this );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainAddress();
    }


    @Override
    public String toString()
    {
        return String.format( "VWC%02d", m_shardno );
    }


    @Override
    public void enqueueMessage(Message msg)
    {
        m_msgstore.enqueueMessage( msg );
    }

    @Override
    public boolean isReady()
    {
        return m_msgstore.isReady();
    }


    @Override
    public boolean execute()
    {
        while( isReady() )
            m_msgstore.execute();

        return true;
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
//        case InternalReconfigurationProtocol.INTERNAL_NEW_CONFIGURATION_ID:
//            initPeers( (ReplicaConfiguration) ((InternalNewConfiguration) msg).getConfiguration() );
//            break;
        case ViewChangeMessages.REQUEST_VIEW_CHANGE_ID:
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
        case ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_READY_ID:
            handleLocalViewChange( msg );
            break;
        case PBFTViewChangeMessages.PBFT_VIEW_CHANGE_ID:
        case ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID:
        case ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID:
        case ViewChangeMessages.NEW_VIEW_READY_ID:
            handleLocalNewView( msg );
            break;
        case PBFTViewChangeMessages.PBFT_NEW_VIEW_ID:
        case ViewChangeMessages.CONFIRM_NEW_VIEW_ID:
        case ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleLocalStableView( msg );
            break;
        default:
            throw new IllegalStateException( msg.toString() );
        }

        return false;
    }


    public void initPeers(AgreementPeers peers)
    {
        m_vccoord = peers.getViewChangeShards().get( peers.getInternalViewChangeCoordinator( 0 ) ).createChannel( domain() );

        Set<DomainEndpoint<PushMessageSink<Message>>> recset = new HashSet<>();
        recset.addAll( peers.getOrderShards() );
        recset.addAll( peers.getCheckpointShards() );
        recset.addAll( peers.getViewChangeShards() );

        m_vclearners = peers.createMulticastChannel( recset, domain() );

        if( m_isintcoord )
        {
            m_vcshards    = peers.createMulticastChannel( peers.getViewChangeShards(), domain() );
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


    private void handleLocalViewChange(Message msg)
    {
        if( !m_locvc.handleMessage( msg ) )
            return;

        if( m_locvc.isViewChangeInitiated() )
            m_locvc.prepareViewChangeShard();
        else if( m_locvc.isViewChangeReady() )
            sendViewChange( m_locvc.getViewNumber(), m_locvc.getCheckpointProof(), m_locvc.getPreparedSet() );
    }

    public void sendViewChange(int viewno, Checkpoint[] chkptproof, OrderNetworkMessage[][] prepproofs)
    {
        PBFTViewChange vc = new PBFTViewChange( m_locrepno, viewno, m_shardno, chkptproof, prepproofs );
        vc.setValid();

        s_logger.debug( "{} send view change {}", this, vc );

        // TODO: Use channels
        m_strongtrans.broadcastMessage( vc );
        handleLocalNewView( vc );
    }


    private void handleLocalNewView(Message msg)
    {
        if( !m_locnv.handleMessage( msg ) )
            return;

        if( m_locnv.isNewViewReady() )
        {
            sendNewView( m_locnv.getNewView() );
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


    private void sendNewView(PBFTNewViewReady nvred)
    {
        // Retrieve all view change messages this shard is responsible for.
        // Fortunately, we still assume a symmetric configuration...
        PBFTViewChange[] nvproof = new PBFTViewChange[ nvred.getNewViewProofs().length ];
        for( int i=0; i<nvproof.length; i++ )
            nvproof[ i ] = nvred.getNewViewProofs()[ i ][ m_shardno ];

        int              viewno      = nvred.getViewNumber();
        PBFTPrePrepare[] newprepreps = createNewPrePrepares( nvproof, nvred.getMinS(), nvred.getMaxS() );

        PBFTNewView nv = new PBFTNewView( m_locrepno, viewno, (byte) m_shardno, nvproof, newprepreps );
        nv.setValid();

        s_logger.debug( "{} send new view {}", this, nv );

        m_strongtrans.broadcastMessage( nv );
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
            m_locrepno  = view.getReplicaGroup().getReplicaNumber();
        }

        m_locvc.initStableView( view );
        m_locnv.initStableView( view );
        m_locsv.initStableView( view );
    }


    private PBFTPrePrepare[] createNewPrePrepares(PBFTViewChange[] nvproof, long mins, long maxs)
    {
        // Okay, let's create the PREPARES for the new view. All prepare certificates in nvproof are ordered,
        // thus we can use a merge.
        ArrayList<PBFTPrePrepare> newprepreps = new ArrayList<>();
        int[] curidxs = new int[ nvproof.length ];
        int   viewno  = nvproof[ 0 ].getViewNumber();

//        InstanceDistribution instdist = Config.ORDER_INSTANCE_DISTRIBUTION;
//        long ns = instdist.getLocalSequenceForInstance( m_shardno, mins+1 );
//        long ni = instdist.getInstanceForLocalSequence( m_shardno, ns );
//
//        while( ni<=maxs )
//        {
//            // Find the smallest order number among the remaining prepare certificates.
//            PBFTPrePrepare npreprep = null;
//
//            for( int i=0; i<nvproof.length; i++ )
//            {
//                OrderNetworkMessage[][] certs = nvproof[ i ].getPrepareCertificates();
//                int idx = curidxs[ i ];
//
//                if( idx<certs.length && ( npreprep==null || certs[ idx ][ 0 ].getOrderNumber()<npreprep.getOrderNumber() ) )
//                    npreprep = (PBFTPrePrepare) certs[ idx ][ 0 ];
//            }
//
//            // Fill the gap with noops if necessary.
//            long ne = npreprep!=null ? npreprep.getOrderNumber() : maxs+1;
//
//            while( ni<ne )
//            {
//                CommandBatch noop = new CommandBatch( m_locrepno, ni, viewno, new Request[ 0 ] );
//                noop.setValid();
//                noop.setCertificateSize( 0 );
//
//                PBFTPrePrepare newpreprep = new PBFTPrePrepare( m_locrepno, ni, viewno, noop );
//                newpreprep.setValid();
//                certifyMessage( newpreprep );
//
//                newprepreps.add( newpreprep );
//
//                ni = instdist.getInstanceForLocalSequence( m_shardno, ++ns );
//            }
//
//            // Apparently, we reached the end at all view change messages.
//            if( npreprep==null )
//                break;
//
//            assert ni==npreprep.getOrderNumber();
//            ni = instdist.getInstanceForLocalSequence( m_shardno, ++ns );
//
//            // Create the next PREPREPARE
//            PBFTPrePrepare newpreprep = new PBFTPrePrepare( m_locrepno, npreprep.getOrderNumber(), viewno, npreprep.getCommand() );
//            certifyMessage( newpreprep );
//
//            newprepreps.add( newpreprep );
//
//            // Increase the indices for all VIEW_CHANGEs that have a certificate for this order number.
//            for( int i=0; i<nvproof.length; i++ )
//            {
//                OrderNetworkMessage[][] certs = nvproof[ i ].getPrepareCertificates();
//                int idx = curidxs[ i ];
//
//                if( idx<certs.length && ( certs[ idx ][ 0 ].getOrderNumber()==npreprep.getOrderNumber() ) )
//                    curidxs[ i ]++;
//            }
//        }
//
//        return newprepreps.toArray( new PBFTPrePrepare[ 0 ] );
        throw new  NotImplementedException();
    }


    private void certifyMessage(NetworkMessage msg)
    {
        m_mapper.certifyAndSerializeMessage( msg, m_defcon.getCertifier() );
    }



    @Override
    public View createView(int viewno)
    {
        return null; //m_repprot.createView( viewno );
    }

    @Override
    public boolean isInternalCoordinator()
    {
        return m_isintcoord;
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

    @Override
    public VerifierGroup getDefaultVerifier()
    {
        return m_defcon;
    }

    @Override
    public VerifierGroup getStrongVerifier()
    {
        return m_strcon;
    }

    @Override
    public VerifierGroup getClientVerifier()
    {
        return m_clicons;
    }

}
