package refit.hybstero.checkpoint;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpoint;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterShardCheckpointStable;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterTCVerification;
import refit.hybstero.view.HybsterViewChangeHandler;
import refit.hybstero.view.HybsterViewChangeMessages.HybsterNewViewStable;
import reptor.chronos.ChronosAddress;
import reptor.chronos.PushMessageSink;
import reptor.chronos.message.SelectorChannel;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.jlib.collect.Slots;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolShard;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessage;
import reptor.replct.agree.common.checkpoint.CheckpointMessages;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointCreated;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.ViewLogging;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class HybsterCheckpointShard implements ProtocolShard, MessageHandler<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterCheckpointShard.class );

    private final short m_shardno;


    private final HybsterCheckpointShardContext m_cntxt;

    private final HybsterViewChangeHandler   m_vwchghandler;

    private final MessageMapper      m_mapper;
    private final VerifierGroup m_reptcverifs;
    private final MessageTransmitter m_reptctrans;
    private final VerifierGroup m_reptmverifs;
    private final MessageTransmitter m_reptmtrans;

    private final Map<Long, HybsterCheckpointInstanceShardCertifier>    m_shardcertifs  = new HashMap<>();
    private final Map<Long, Slots<HybsterCheckpointCertificate>> m_shardcollects = new HashMap<>();

    private long                            m_stablechkptno   = -1;

    // Configuration dependent
    private AgreementPeers m_config;

    private PushMessageSink<CheckpointMessage>         m_chkptcoord;
    private PushMessageSink<CheckpointStable>          m_chkptlearner;
    private PushMessageSink<CheckpointMessage>         m_chkptsentlearner;

    // View dependent
    private View            m_stableview = null;
    private int             m_curviewno  = -1;

    // Group dependent
    private final byte                      m_repno;
    private final ReplicaPeerGroup m_grpconf;


    public HybsterCheckpointShard(HybsterCheckpointShardContext cntxt, short shardno, ReplicaPeerGroup grpconf)
    {
        m_cntxt       = cntxt;
        m_shardno     = shardno;
        m_repno       = grpconf.getReplicaNumber();
        m_grpconf     = grpconf;

        m_mapper   = cntxt.getMessageMapper();
        m_reptcverifs = cntxt.getReplicaTCVerifiers();
        m_reptmverifs = cntxt.getReplicaTMVerifiers();
        m_reptctrans  = cntxt.getReplicaTCTransmitter();
        m_reptmtrans  = cntxt.getReplicaTMTransmitter();

        m_vwchghandler = new HybsterViewChangeHandler( cntxt.getDomainContext(), shardno );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainContext().getDomainAddress();
    }


    @Override
    public String toString()
    {
        return String.format( "CHK%02d", m_shardno );
    }

    @Override
    public final short getShardNumber()
    {
        return m_shardno;
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case HybsterCheckpointMessages.HYBSTER_CHECKPOINT_ID:
            handleCheckpoint( (HybsterCheckpoint) msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_TC_VERIFICATION_ID:
            handleTCVerification( (HybsterTCVerification) msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_SHARD_CHECKPOINT_STABLE_ID:
            handleStableShardCheckpoint( (HybsterShardCheckpointStable) msg );
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
            throw new IllegalArgumentException( msg.toString() );
        }

        // TODO: What is the external state? The last stable checkpoint, view, configuration? Everything?
        return false;
    }


    public void initPeers(AgreementPeers peers)
    {
        m_config           = peers;
        m_chkptcoord       = createCheckpointCoordinator( peers );
        m_chkptlearner     = createCheckpointLearner( peers );
        m_chkptsentlearner = createCheckpointSentLearner( peers );

        m_vwchghandler.initPeers( peers );
    }


    private void handleViewChange(Message msg)
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

//case PREPARE_VIEW_CHANGE:
//    handleInternalViewChangeRequest( (PrepareViewChange) msg );
//    break;
//case NEW_VIEW_STABLE:
//    handleInternalNewView( (PBFTNewViewStable) msg );
//    break;


    // TODO: Should become part of the internal view change protocol.
//    private void handleInternalViewChangeRequest(PrepareViewChange vcreq)
//    {
//        if( vcreq.getViewNumber()<=m_curviewno )
//            return;
//
//        m_curviewno = vcreq.getViewNumber();
//
//        Checkpoint[]     chkpts = null;
//        List<Checkpoint> proof  = null; //m_stablechkpt.checkpoints.getDecidingBallots();
//
////        if( proof!=null )
////            chkpts = proof.subList( 0, Config.REGULAR_CHECKPOINT_STABILITY_THRESHOLD ).toArray( new Checkpoint[ 0 ] );
//
//        InternalViewChangeShardMessage vc = new PBFTCheckpointShardViewChange( m_shardno, m_curviewno, chkpts );
//        m_intvccoord.enqueueMessage( vc );
//    }


    public void initStableView(HybsterNewViewStable nv)
    {
        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, nv.getViewNumber() );

        m_stableview = nv.getView();
        m_curviewno  = nv.getViewNumber();
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
//        assert isCurrentViewStable();
//
//        if( !Config.CHECKPOINT_MODE.includes( CheckpointMode.SEND ) )
//            return;
//
//        // The execution stage sends internal checkpoints to all shards, that is, all get the messages in the
//        // same order. Since checkpoints can become stable only if own checkpoints are sent, no later checkpoint
//        // can interfere and all shards will send a checkpoint message.
//        assert intchkpt.getOrderNumber()>m_stablechkptno;
//        assert !Config.DISTRIBUTE_CHECKPOINT_HASHES;
//
//        boolean iscoord = isCoordinator( intchkpt.getOrderNumber() );
//
//        ImmutableData data = iscoord ? intchkpt.getServiceState() : null;
//        long[] nodeprogs = iscoord ? intchkpt.getResultMap() : null;
//
//         HybsterCheckpoint ownchkpt =
//                 new HybsterCheckpoint( m_repno, m_curviewno, intchkpt.getOrderNumber(), data,
//                                        nodeprogs, intchkpt.isFullServiceState(), m_shardno );
//         ownchkpt.setValid();
//        sendCheckpoint( ownchkpt );
        throw new  NotImplementedException();
    }


    private void sendCheckpoint(HybsterCheckpoint ownchkpt)
    {
        s_logger.debug( "{} send own checkpoint {}", this, ownchkpt );

        m_reptctrans.broadcastMessage( ownchkpt );

        HybsterCheckpointInstanceShardCertifier certif = getInstance( ownchkpt.getOrderNumber() );
        certif.addCheckpoint( ownchkpt, true );

        if( m_chkptsentlearner!=null )
            m_chkptsentlearner.enqueueMessage( ownchkpt );

        assert !certif.isStable();
    }


    private void handleCheckpoint(HybsterCheckpoint chkpt)
    {
        assert isCurrentViewStable();

        s_logger.debug( "{} received checkpoint {}", this, chkpt );

        // Send a verification even if it was an outdated checkpoint. The sender needs enough of these
        // messages to proceed.
        sendTCVerification( chkpt );

        // Do not process old checkpoints
        if( chkpt.getOrderNumber()<=m_stablechkptno )
            return;

        HybsterCheckpointInstanceShardCertifier certif = getInstance( chkpt.getOrderNumber() );

        if( certif.addCheckpoint( chkpt, false ) )
            shardCheckpointStable( certif.getCertificate() );
    }


    private void sendTCVerification(HybsterCheckpoint chkpt)
    {
        HybsterTCVerification tcack = HybsterTCVerification.createFor( m_repno, chkpt );
        tcack.setValid();

        s_logger.debug( "{} send tc verification {}", this, tcack );

        m_reptmtrans.unicastMessage( tcack, chkpt.getSender() );
    }


    private void handleTCVerification(HybsterTCVerification tcack)
    {
        s_logger.debug( "{} received tc verification {}", this, tcack );

        if( tcack.getOrderNumber()<=m_stablechkptno )
            return;

        HybsterCheckpointInstanceShardCertifier certif = getInstance( tcack.getOrderNumber() );

        if( certif.addTCVerification( tcack, false ) )
            shardCheckpointStable( certif.getCertificate() );
    }


    private void shardCheckpointStable(HybsterCheckpointCertificate cert)
    {
        s_logger.debug( "{} stable shard checkpoint for {}", this, cert.getOrderNumber() );

        boolean iscoord = isCoordinator( cert.getOrderNumber() );
        HybsterShardCheckpointStable stabchkpt = new HybsterShardCheckpointStable( cert );

        if( iscoord )
            handleStableShardCheckpoint( stabchkpt );
        else
            m_chkptcoord.enqueueMessage( stabchkpt );
    }


    private void handleStableShardCheckpoint(HybsterShardCheckpointStable stabchkpt)
    {
        if( stabchkpt.getOrderNumber()<=m_stablechkptno )
            return;

        Slots<HybsterCheckpointCertificate> coll = getShardCollector( stabchkpt.getOrderNumber() );

        assert !coll.containsKey( stabchkpt.getShardNumber() );

        coll.put( stabchkpt.getShardNumber(), stabchkpt.getShardCertificate() );

        if( coll.emptySlotsCount()==0 )
        {
            checkpointStable( stabchkpt.getOrderNumber(), coll.toArray( new HybsterCheckpointCertificate[ 0 ] ) );
            distributeStableCheckpoint( stabchkpt.getOrderNumber() );
        }
    }


    private void distributeStableCheckpoint(long orderno)
    {
        // Distribute progress notification
        CheckpointStable stablechkpt = new CheckpointStable( orderno );

        // Create and forward checkpoint operation
        m_chkptlearner.enqueueMessage( stablechkpt );
    }


    private void handleStableCheckpoint(CheckpointStable stabchkpt)
    {
        if( stabchkpt.getOrderNumber()<=m_stablechkptno )
            return;

        checkpointStable( stabchkpt.getOrderNumber(), null );
    }


    private void checkpointStable(long orderno, HybsterCheckpointCertificate[] cert)
    {
        assert m_stablechkptno<orderno;

        m_stablechkptno   = orderno;

        m_shardcertifs.keySet().removeIf( x -> x<=m_stablechkptno );
        m_shardcollects.keySet().removeIf( x -> x<=m_stablechkptno );

        s_logger.debug( "{} stable checkpoint for order number {}, running {}/{}",
                        this, orderno, m_shardcertifs.size(), m_shardcollects.size() );
    }


    private boolean isCoordinator(long orderno)
    {
        return m_config.getInternalCheckpointCoordinator( orderno )==m_shardno;
    }


    private HybsterCheckpointInstanceShardCertifier getInstance(long orderno)
    {
        HybsterCheckpointInstanceShardCertifier cert = m_shardcertifs.get( orderno );

        if( cert==null )
        {
            cert = new HybsterCheckpointInstanceShardCertifier( m_mapper, m_reptcverifs, m_reptmverifs, m_grpconf )
                                .initConfig( m_repno, m_shardno ).initJob( orderno );
            m_shardcertifs.put( orderno, cert );
        }

        return cert;
    }


    private Slots<HybsterCheckpointCertificate> getShardCollector(long orderno)
    {
//        Slots<HybsterCheckpointCertificate> coll = m_shardcollects.get( orderno );
//
//        if( coll==null )
//        {
//            coll = new Slots<>( Config.CHECKPOINTSTAGE.getNumber() );
//            m_shardcollects.put( orderno, coll );
//        }
//
//        return coll;
        throw new  NotImplementedException();
    }


    private PushMessageSink<CheckpointMessage> createCheckpointCoordinator(AgreementPeers peers)
    {
        Function<CheckpointMessage, Integer> selector =
                msg -> peers.getInternalCheckpointCoordinator( msg.getOrderNumber() );

        return new SelectorChannel<>( peers.createChannels( peers.getOrderShards(), domain() ), selector );
    }


//    private static MessageChannel<CheckpointMessage> createCheckpointFollower(Configuration config)
//    {
//        BiFunction<CheckpointMessage, Integer, Boolean> filter =
//                (msg, no) -> no!=config.getInternalCheckpointCoordinator( msg.getOrderNumber() ).getNumber();
//
//        return new FilterChannel<>( Configuration.createChannels( config.getOrderShardProcessors() ), filter );
//    }


    private PushMessageSink<CheckpointStable> createCheckpointLearner(AgreementPeers config)
    {
//        if( !Config.CHECKPOINTSTAGE.propagateToAll() )
//        {
//            Function<CheckpointStable, Integer> selector =
//                    msg -> config.getInternalOrderCoordinator( msg.getOrderNumber() );
//
//            return new SelectorChannel<>( ReplicaConfiguration.createChannels( config.getOrderShards(), domain() ), selector );
//        }
//        else
//        {
//            // TODO: The coordinator should not be included.
//            Set<OutboundChannelEndpoint> recset = new HashSet<>();
//            recset.addAll( config.getOrderShards() );
//            recset.addAll( config.getCheckpointShards() );
//
//            return ReplicaConfiguration.createMulticastChannel( recset, domain() );
//        }
        throw new  NotImplementedException();
    }


//    private static OutboundChannel<InternalViewChangeMessage> createInternalViewChangeCoordinator(ReplicaConfiguration config)
//    {
//        Function<InternalViewChangeMessage, Integer> selector =
//                msg -> config.getInternalViewChangeCoordinator( msg.getViewNumber() ).getNumber();
//        return new SelectorChannel<>( ReplicaConfiguration.createChannels( config.getViewChangeShardProcessors() ), selector );
//    }


    private PushMessageSink<CheckpointMessage> createCheckpointSentLearner(AgreementPeers config)
    {
//        if( Config.HYBSTER_ASYNCHONOUS_CHECKPOINTS )
//            return null;
//        else
//            return ReplicaConfiguration.createMulticastChannel( config.getOrderShards(), domain() );
        throw new  NotImplementedException();
    }

}
