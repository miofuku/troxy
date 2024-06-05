package refit.pbfto.checkpoint;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.common.stores.ActiveMessageQueue;
import refit.common.stores.ActiveMessageStore;
import refit.pbfto.PBFTProtocolShard;
import refit.pbfto.suite.PBFT;
import refit.pbfto.view.PBFTViewChangeHandler;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewViewStable;
import reptor.chronos.Actor;
import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomainContext;
import reptor.chronos.PushMessageSink;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointCreated;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTCheckpointShard extends PBFTProtocolShard
                                 implements MessageHandler<Message>, Actor, PushMessageSink<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTCheckpointShard.class );

    private final ChronosDomainContext m_cntxt;
    private final MessageTransmitter         m_reptrans;

    private final Map<Long, PBFTCheckpointCertifier>  m_certificates;
    private long                                    m_stablechkptno   = -1;
    private Checkpoint[]                            m_stablechkptcert = null;

    private final ActiveMessageStore<Message>       m_msgstore;

    private final PBFTViewChangeHandler             m_vchandler;

    // Configuration dependent
    private PushMessageSink<Message>                m_snapshotlearner;
    private PushMessageSink<CheckpointStable>       m_chkptlearner;

    // View dependent
    private View            m_stableview    = null;
    private int             m_curviewno     = -1;
    private boolean         m_selfrecipient = false;
    private byte            m_repno;
    private byte            m_nreplicas;


    public PBFTCheckpointShard(ChronosDomainContext cntxt, PBFT repprot, short shardno, ReplicaPeerGroup grpconf,
                               MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( repprot, shardno, reptrans );

        m_cntxt = cntxt;
        m_certificates = new HashMap<Long, PBFTCheckpointCertifier>();

        m_msgstore     = new ActiveMessageQueue<>( this );
        m_reptrans     = new MessageTransmitter( m_mapper, m_defcon.getCertifier(), reptrans );
        m_vchandler    = new PBFTViewChangeHandler( cntxt, shardno );

        m_repno     = grpconf.getReplicaNumber();
        m_nreplicas = grpconf.size();
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainAddress();
    }


    @Override
    public String toString()
    {
        return String.format( "CHK%02d", m_shardno );
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
        return m_msgstore.execute();
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        // TODO: Currently, it does not work during a view change. However, only a group reconfiguration would
        //       affect the sender number and a group reconfiguration is a completely other story.
        // TODO: All the state checking should be handled during the reception of messages. If we have synchronous
        //       calls, the master is responsible for filtering out messages that do not apply to the current state.
        //       As it is done with order instances and messages for other views.
        switch( msg.getTypeID() )
        {
//        case InternalReconfigurationProtocol.INTERNAL_NEW_CONFIGURATION_ID:
//            initConfig( (ReplicaConfiguration) ((InternalNewConfiguration) msg).getConfiguration() );
//            break;
        case CheckpointMessages.CHECKPOINT_ID:
            handleCheckpoint( (Checkpoint) msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            handleStableCheckpoint( (CheckpointStable) msg );
            break;
        case CheckpointMessages.CHECKPOINT_CREATED_ID:
            handleInternalCheckpoint( (CheckpointCreated) msg );
            break;
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            handleViewChange( msg );
            break;
        default:
            throw new IllegalStateException( msg.toString() );
        }

        // TODO: What is the external state? The last stable checkpoint, view, configuration? Everything?
        return true;
    }


    public void initPeers(AgreementPeers config)
    {
        m_snapshotlearner = createSnapshopLearner( m_shardno, config );
        m_chkptlearner    = createCheckpointLearner( config );

        m_vchandler.initPeers( config );
    }


    private void handleViewChange(Message msg)
    {
        if( !m_vchandler.handleMessage( msg ) )
            return;

        if( m_vchandler.isViewChangeInitiated() )
        {
            m_curviewno  = m_vchandler.getViewNumber();
            m_stableview = null;

            m_vchandler.prepareCheckpointShard( m_stablechkptcert );
        }
        else
        {
            assert m_vchandler.isViewStable();

            initStableView( m_vchandler.getStableView() );
        }
    }


    private void initStableView(PBFTNewViewStable nv)
    {
        // TODO: Use checkpoint latest certificate of nv
        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();

//        m_selfrecipient = Shorts.contains( m_stableview.getCheckpointRecipients(), m_repno );
    }


    private boolean isCurrentViewStable()
    {
        return m_curviewno==m_stableview.getNumber();
    }


    // #######################
    // # REGULAR CHECKPOINTS #
    // #######################

    private void handleInternalCheckpoint(CheckpointCreated intchkpt)
    {
        assert isCurrentViewStable();

        if( intchkpt.getOrderNumber()<=m_stablechkptno )
            return;

        // We include the current view number of this shard, that is, if a view change happens, this
        // it could be different for each message shard. However, if we had a real group reconfiguration,
        // we would have to do a lot things much differently, anyway.

//        if( Config.EXECUTIONSTAGE.getNumber()==1 )
//        {
//            Checkpoint chkpt = new Checkpoint( m_repno, (short) 0, m_curviewno, intchkpt.getOrderNumber(),
//                                               intchkpt.getServiceState(), intchkpt.getResultMap(), intchkpt.isFullServiceState() );
//            chkpt.setValid();
//
//            handleCheckpoint( chkpt );
//        }
//        else
//        {
//            PBFTCheckpointCertifier certificate = getCertificate( intchkpt.getOrderNumber() );
//            certificate.add( intchkpt );
//
//            if( certificate.nr_part_chks==Config.EXECUTIONSTAGE.getNumber() )
//            {
//                ByteBuffer buf   = ByteBuffer.allocate( certificate.total_chkpt_size +
//                                                        Integer.BYTES*Config.EXECUTIONSTAGE.getNumber() );
//                long[] nodeprogs = Config.CHECKPOINT_MODE.includes( CheckpointMode.NODE_PROGRESS ) ?
//                                        new long[ m_nreplicas + Config.TOTAL_NR_OF_CLIENTS ] : null;
//                boolean isfull   = true;
//
//                for( CheckpointCreated p : certificate.partchks )
//                {
//                    isfull = isfull && p.isFullServiceState();
//                    buf.putInt( p.getServiceState().size() );
//                    p.getServiceState().writeTo( buf );;
//
//                    if( Config.CHECKPOINT_MODE.includes( CheckpointMode.NODE_PROGRESS ) )
//                        for( int i = m_nreplicas + p.getPartitionNumber(); i<nodeprogs.length;
//                                i += Config.EXECUTIONSTAGE.getNumber() )
//                            nodeprogs[ i ] = p.getResultMap()[ i ];
//                }
//
//                Checkpoint chkpt = new Checkpoint( m_repno, (short) 0, m_curviewno, certificate.agreementSeqNr, ImmutableData.wrap( buf.array() ), nodeprogs, isfull );
//
//                handleCheckpoint( chkpt );
//            }
//        }
        throw new  NotImplementedException();
    }


    private void handleCheckpoint(Checkpoint chkpt)
    {
        assert isCurrentViewStable();

        // Do not process old checkpoints
        // We could forward a full checkpoint to execution stage, even if it is old, as the execution stage
        // currently might only have a checkpoint operation whose stability is based on hashes.
        // However, this requires that verification of the old checkpoint is done in the execution stage,
        // which in turn requires that the execution maintains a connection record itself ... for a case
        // that is how likely? Don't know.
        if( chkpt.getOrderNumber()<=m_stablechkptno )
            return;

        // Distribute own checkpoint
//        if( Config.CHECKPOINT_MODE.includes( CheckpointMode.SEND ) && chkpt.getSender()==m_repno )
//        {
//            Checkpoint ownCheckpoint;
//
//            if( Config.DISTRIBUTE_CHECKPOINT_HASHES )
//            {
//                ownCheckpoint = new Checkpoint( chkpt.getSender(), chkpt.getShardNumber(), chkpt.getViewNumber(), chkpt.getOrderNumber(),
//                            m_mapper.digestMessageContent( chkpt ).getContentDigest(), chkpt.getResultMap(), false );
//                ownCheckpoint.setValid();
//            }
//            else
//            {
//                ownCheckpoint = chkpt;
//            }
//
//            s_logger.debug( "{} distribute own checkpoint {}", this, ownCheckpoint );
//
//            m_reptrans.broadcastMessage( ownCheckpoint );
//        }
//
//        // Only store checkpoint if the local replica is a checkpoint recipient
//        if( !m_selfrecipient )
//            return;
//
//        // Get checkpoint certificate
//        PBFTCheckpointCertifier certificate = getCertificate( chkpt.getOrderNumber() );
//
//        // Store checkpoint
//        s_logger.debug( "{} received checkpoint {}", this, chkpt );
//        boolean success = certificate.add( chkpt, chkpt.getSender()==m_repno );
//        if( !success )
//        {
//            s_logger.warn( "{} bad CHECKPOINT ({})", this, chkpt );
//            return;
//        }
//
//        // Check whether the checkpoint became stable
//        if( !certificate.isStable() )
//            return;
//
//        Checkpoint[] proof  = certificate.checkpoints.getDecidingBallots()
//                                                     .subList( 0, Config.REGULAR_CHECKPOINT_STABILITY_THRESHOLD )
//                                                     .toArray( new Checkpoint[ 0 ] );
//
//        // Stable checkpoint reached
//        checkpointStable( certificate.agreementSeqNr, proof );
//
//        // Forward information that the checkpoint has become stable
//        sendStableCheckpoint( certificate.getStableCheckpoint(), certificate );
        throw new  NotImplementedException();
    }


    private PBFTCheckpointCertifier getCertificate(long orderno)
    {
        PBFTCheckpointCertifier cert = m_certificates.get( orderno );

        if( cert==null )
        {
            cert = new PBFTCheckpointCertifier( orderno, m_mapper, m_defcon );
            m_certificates.put( orderno, cert );
        }

        return cert;
    }


    private void handleStableCheckpoint(CheckpointStable stabchkpt)
    {
        if( stabchkpt.getOrderNumber()<=m_stablechkptno )
            return;

        checkpointStable( stabchkpt.getOrderNumber(), null );
    }

    private void checkpointStable(long orderno, Checkpoint[] cert)
    {
        assert m_stablechkptno<orderno;

        s_logger.debug( "{} stable checkpoint for order number {}", this, orderno );

        m_stablechkptno   = orderno;
        m_stablechkptcert = cert;

        m_certificates.keySet().removeIf( x -> x<=m_stablechkptno );
    }


    private void sendStableCheckpoint(Checkpoint chkpt, PBFTCheckpointCertifier cert)
    {
        // Distribute progress notification
        CheckpointStable stablechkpt = new CheckpointStable( chkpt.getOrderNumber() );

        // Create and forward checkpoint operation
        m_chkptlearner.enqueueMessage( stablechkpt );

//        if( m_snapshotlearner!=null )
//        {
//            Operation operation;
//
//            if( chkpt.isFullCheckpoint() )
//            {
//                // Create checkpoint operation with full stable checkpoint
//                operation = new Operation( ExecutionMessages.SNAPSHOT_ID, (short) -1, chkpt.getOrderNumber(), chkpt );
//            }
//            else
//            {
//                // Create checkpoint operation with stable checkpoint hash
//                operation = new Operation( ExecutionMessages.SNAPSHOT_ID, (short) -1, chkpt.getOrderNumber(), m_mapper.digestMessageContent( chkpt ) );
//            }
//
//            m_snapshotlearner.enqueueMessage( operation );
//        }
    }


    private PushMessageSink<Message> createSnapshopLearner(int shardno, AgreementPeers config)
    {
//        if( !Config.CHECKPOINT_MODE.includes( CheckpointMode.APPLY ) )
//            return null;
//        else if( !Config.CHECKPOINTSTAGE.propagateToAll() )
//            return config.getExecutionShards().get( Config.CHECKPOINTSTAGE.getExecutionStageID( shardno ) ).createChannel( domain() );
//        else
//            return ReplicaConfiguration.createMulticastChannel( config.getExecutionShards(), domain() );
//
        throw new  NotImplementedException();
    }


    private PushMessageSink<CheckpointStable> createCheckpointLearner(AgreementPeers config)
    {
//        if( !Config.CHECKPOINTSTAGE.propagateToAll() )
//        {
//            Function<CheckpointStable, Integer> selector =
//                    msg -> config.getInternalOrderCoordinator( msg.getOrderNumber() );
//            return new SelectorChannel<>( ReplicaConfiguration.createChannels( config.getOrderShards(), domain() ), selector );
//        }
//        else
//        {
//            Set<OutboundChannelEndpoint> recset = new HashSet<>();
//            recset.addAll( config.getOrderShards() );
//            recset.addAll( config.getCheckpointShards() );
//
//            return ReplicaConfiguration.createMulticastChannel( recset, domain() );
//        }
        throw new  NotImplementedException();
    }

}
