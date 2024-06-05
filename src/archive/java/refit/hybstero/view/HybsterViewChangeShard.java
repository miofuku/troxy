package refit.hybstero.view;

import reptor.distrbt.com.Message;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolShard;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.view.ViewChangeMessages;


public class HybsterViewChangeShard implements ProtocolShard, MessageHandler<Message>
//                                            LocalViewChangePreparationProtocol.Context,
//                                            PBFTLocalNewViewProtocol.Context,
//                                            PBFTLocalStableViewProtocol.Context
{

    private final short m_shardno;

//    private final boolean m_isintcoord;
//
//    private final PBFTLocalViewChangePreparationHandler m_locvc;
//    private final PBFTLocalNewViewProtocol.Handler      m_locnv;
//    private final PBFTLocalStableViewProtocol.Handler   m_locsv;
//    private final ReplicaTransmissionHandler            m_strongtrans;
//
//    private View m_groupview = null;
//    private byte m_locrepno  = -1;
//
//    private OutboundChannel<Message> m_vcacceptors = null; // All order, checkpoint, and view change shards
//    private OutboundChannel<Message> m_vcshards    = null;
//    private OutboundChannel<Message> m_vccoord     = null;
//    private OutboundChannel<Message> m_vclearners  = null;


    public HybsterViewChangeShard(short shardno)
    {
        m_shardno = shardno;

//        super( repprot, shardno );
//
//        m_isintcoord  = shardno==0;
//
//        m_strongtrans = new ReplicaTransmissionHandler( m_strongcertif, repgate );
//        m_locvc       = new PBFTLocalViewChangePreparationHandler( this );
//        m_locnv       = new PBFTLocalNewViewProtocol.Handler( this );
//        m_locsv       = PBFTLocalStableViewProtocol.Handler.createForAcceptor( this );
    }


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


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
//        case REQUEST_VIEW_CHANGE:
//        case PREPARE_VIEW_CHANGE:
//        case ORDER_SHARD_VIEW_CHANGE:
//        case CHECKPOINT_SHARD_VIEW_CHANGE:
//        case VIEW_SHARD_VIEW_CHANGE:
//        case VIEW_CHANGE_READY:
//            handleLocalViewChange( msg );
//            break;
//        case PBFT_VIEW_CHANGE:
//        case CONFIRM_VIEW_CHANGE:
//        case VIEW_CHANGE_CONFIRMED:
//        case NEW_VIEW_READY:
//            handleLocalNewView( msg );
//            break;
//        case PBFT_NEW_VIEW:
//        case CONFIRM_NEW_VIEW:
//        case NEW_VIEW_SHARD_CONFIRMED:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleLocalStableView( msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }

        return false;
    }


    public void initPeers(AgreementPeers config)
    {
//        m_vccoord    = config.getInternalViewChangeCoordinator( 0 ).createChannel();
//
//        m_vclearners = Configuration.createMulticastChannel( config.getOrderShardProcessors(),
//                                                             config.getCheckpointShardProcessors(),
//                                                             config.getViewChangeShardProcessors(),
//                                                             config.getViewChangeShardProcessors() );
//
//        if( m_isintcoord )
//        {
//            m_vcshards    = Configuration.createMulticastChannel( config.getViewChangeShardProcessors() );
//            m_vcacceptors = Configuration.createMulticastChannel( config.getOrderShardProcessors(),
//                                                                  config.getCheckpointShardProcessors(),
//                                                                  config.getViewChangeShardProcessors() );
//        }
//        else
//        {
//            m_vcshards    = null;
//            m_vcacceptors = null;
//        }
//
//        m_locvc.initConfig( config );
//        m_locnv.initConfig( config );
//        m_locsv.initConfig( config );
    }


//    private void handleLocalViewChange(Message msg)
//    {
//        if( !m_locvc.handleMessage( msg ) )
//            return;
//
//        if( m_locvc.isViewChangeInitiated() )
//            m_locvc.prepareViewChangeShard();
//        else if( m_locvc.isViewChangeReady() )
//            sendViewChange( m_locvc.getViewNumber(), m_locvc.getCheckpointProof(), m_locvc.getPreparedSet() );
//    }
//
//    public void sendViewChange(int viewno, Checkpoint[] chkptproof, OrderProtocolMessage[][] prepproofs)
//    {
//        PBFTViewChange vc = new PBFTViewChange( viewno, m_shardno, chkptproof, prepproofs, m_locrepno );
//
//        if( Logger.LOG_VIEW )
//            Logger.logView( this, "send view change " + vc );
//
//        // TODO: Use channels
//        m_strongtrans.handleTransmission( new ReplicaTransmission.BroadcastToReplicas( vc, null ) );
//        handleLocalNewView( vc );
//    }
//
//
//    private void handleLocalNewView(Message msg)
//    {
//        if( !m_locnv.handleMessage( msg ) )
//            return;
//
//        if( m_locnv.isNewViewReady() )
//        {
//            sendNewView( m_locnv.getNewView() );
//        }
//        else if( m_locnv.isViewChangeAgreed() )
//        {
//            // TODO: Start timer on (group) followers.
//        }
//        else if( m_locnv.isViewChangeConfirmed() )
//        {
//            // TODO: "Second, if a replica receives a set of ￼f+1 valid view-change messages from other replicas
//            //       for views greater than its current view, it sends a view-change message for the smallest view in the set"
//            //       -> m_vcinit.initiateViewChange()
//        }
//    }
//
//
//    private void sendNewView(PBFTNewViewReady nvred)
//    {
//        // Retrieve all view change messages this shard is responsible for.
//        // Fortunately, we still assume a symmetric configuration...
//        PBFTViewChange[] nvproof = new PBFTViewChange[ nvred.getNewViewProofs().length ];
//        for( int i=0; i<nvproof.length; i++ )
//            nvproof[ i ] = nvred.getNewViewProofs()[ i ][ m_shardno ];
//
//        int              viewno      = nvred.getViewNumber();
//        PBFTPrePrepare[] newprepreps = createNewPrePrepares( nvproof, nvred.getMinS(), nvred.getMaxS() );
//
//        PBFTNewView nv = new PBFTNewView( viewno, (byte) m_shardno, nvproof, newprepreps, m_locrepno );
//
//        if( Logger.LOG_VIEW )
//            Logger.logView( this, "send new view " + nv );
//
//        m_strongtrans.handleTransmission( new ReplicaTransmission.BroadcastToReplicas( nv, null ) );
//        handleLocalStableView( nv );
//    }
//
//
    private void handleLocalStableView(Message msg)
    {
//        if( !m_locsv.handleMessage( msg ) )
//            return;
//
//        View view = m_locsv.getNewView().getView();
//
//        if( m_groupview==null )
//        {
//            m_groupview = view;
//            m_locrepno  = view.getReplicaNumber();
//        }
//
//        m_locvc.initStableView( view );
//        m_locnv.initStableView( view );
//        m_locsv.initStableView( view );
    }
//
//
//    private PBFTPrePrepare[] createNewPrePrepares(PBFTViewChange[] nvproof, long mins, long maxs)
//    {
//        // Okay, let's create the PREPARES for the new view. All prepare certificates in nvproof are ordered,
//        // thus we can use a merge.
//        ArrayList<PBFTPrePrepare> newprepreps = new ArrayList<>();
//        int[] curidxs = new int[ nvproof.length ];
//        int   viewno  = nvproof[ 0 ].getViewNumber();
//
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
//                OrderProtocolMessage[][] certs = nvproof[ i ].getPrepareCertificates();
//                int idx = curidxs[ i ];
//
//                if( idx<certs.length && ( npreprep==null || certs[ idx ][ 0 ].getInstanceID()<npreprep.getInstanceID() ) )
//                    npreprep = (PBFTPrePrepare) certs[ idx ][ 0 ];
//            }
//
//            // Fill the gap with noops if necessary.
//            long ne = npreprep!=null ? npreprep.getInstanceID() : maxs+1;
//
//            while( ni<ne )
//            {
//                PBFTPrePrepare newpreprep = new PBFTPrePrepare( ni, viewno, Request.NO_OP, m_locrepno );
//                m_defaultcertif.prepareTransmission( newpreprep );
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
//            assert ni==npreprep.getInstanceID();
//            ni = instdist.getInstanceForLocalSequence( m_shardno, ++ns );
//
//            // Create the next PREPREPARE
//            PBFTPrePrepare newpreprep = new PBFTPrePrepare( npreprep.getInstanceID(), viewno, npreprep.getCommand(), m_locrepno );
//            m_defaultcertif.prepareTransmission( newpreprep );
//
//            newprepreps.add( newpreprep );
//
//            // Increase the indices for all VIEW_CHANGEs that have a certificate for this order number.
//            for( int i=0; i<nvproof.length; i++ )
//            {
//                OrderProtocolMessage[][] certs = nvproof[ i ].getPrepareCertificates();
//                int idx = curidxs[ i ];
//
//                if( idx<certs.length && ( certs[ idx ][ 0 ].getInstanceID()==npreprep.getInstanceID() ) )
//                    curidxs[ i ]++;
//            }
//        }
//
//        return newprepreps.toArray( new PBFTPrePrepare[ 0 ] );
//    }
//
//
//
//    @Override
//    public View createView(int viewno)
//    {
//        return m_repprot.createView( viewno );
//    }
//
//    @Override
//    public boolean isInternalCoordinator()
//    {
//        return m_isintcoord;
//    }
//
//    @Override
//    public void enqueueForViewChangeCoordinator(Message msg)
//    {
//        m_vccoord.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForViewChangeAcceptors(Message msg)
//    {
//        m_vcacceptors.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForViewChangeLearners(Message msg)
//    {
//        m_vcshards.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForNewViewAcceptors(Message msg)
//    {
//        m_vcshards.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForNewViewLearners(Message msg)
//    {
//        m_vcshards.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForStableViewAcceptors(Message msg)
//    {
//        m_vcshards.enqueueMessage( msg );
//    }
//
//    @Override
//    public void enqueueForStableViewLearners(Message msg)
//    {
//        m_vclearners.enqueueMessage( msg );
//    }
//
//    @Override
//    public MessageVerifier getDefaultVerifier()
//    {
//        return m_defaultcertif;
//    }
//
//    @Override
//    public MessageVerifier getStrongVerifier()
//    {
//        return m_strongcertif;
//    }
//
//    @Override
//    public MessageVerifier getClientVerifier()
//    {
//        return m_clientcertif;
//    }

}
