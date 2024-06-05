package refit.hybstero.suite;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.hybstero.checkpoint.HybsterCheckpointMessages;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpoint;
import refit.hybstero.checkpoint.HybsterCheckpointShard;
import refit.hybstero.checkpoint.HybsterCheckpointShardContext;
import refit.hybstero.order.HybsterOrderMessages;
import refit.hybstero.order.HybsterOrderShard;
import refit.hybstero.order.HybsterOrderVariant;
import refit.hybstero.view.HybsterViewChangeMessages;
import refit.hybstero.view.HybsterViewChangeShard;
import reptor.chronos.ChronosTask;
import reptor.chronos.SchedulerContext;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.certify.trusted.SequentialCounterGroupCertifier;
import reptor.distrbt.certify.trusted.TrustedMacGroupCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages;
import reptor.replct.agree.common.order.OrderExtensions;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.common.InstanceDistribution;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.connect.ConnectionCertifierCollection;
import reptor.replct.invoke.InvocationMessages;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class HybsterShardProcessor extends ProtocolShardModule
                                   implements HybsterCheckpointShardContext, HybsterOrderShard.Context, MessageHandler<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterShardProcessor.class );

    private final HybsterOrderShard          m_orderhandler;
    private final HybsterCheckpointShard     m_chkpthandler;
    private final HybsterViewChangeShard     m_vwchghandler;

    private final MessageMapper              m_mapper;
    private final SequentialCounterGroupCertifier    m_tccons;
    private final TrustedMacGroupCertifier    m_tmcons;
    private final ConnectionCertifierCollection m_clicons;
    private final MessageTransmitter         m_reptctrans;
    private final MessageTransmitter         m_reptmtrans;
    private final MessageTransmitter         m_reptrans;

    private final ReplicaMessageStream[]     m_msgstreams;

    private final HybsterOrderVariant        m_ordervariant;
    private final int                        m_ordercommitthreshold;
    private final boolean                    m_skipordermessages;
    private final InstanceDistribution       m_orderinstdist;
    private final int                        m_ordershardexec;
    private final int                        m_orderwndsize;
    private final int                        m_orderwndshard;
    private final int                        m_actorderwnd;
    private final int                        m_actorderwndshard;
    private final boolean                    m_unboundorderwnd;
    private final int                        m_minbatchsize;
    private final int                        m_maxbatchsize;
    private final boolean                    m_asyncchkpts;
    private final int                        m_chkptint;

    private short m_repno;

    // TODO: Replace message queue for reconfiguration.
    //       1. Move relevant message in the current queue to a second one.
    //       2. From then, add new incoming RC messages to the second queue and all others to the first one.
    //       3. Process only the second queue.
    //       4. If the second queue is empty and a stable configuration reached, switch back to the main queue.
//    private final Queue<Message> m_reconfstore = new LinkedList<>();


    public HybsterShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short shardno, Hybster repprot, ReplicaPeerGroup grpconf,
            OrderExtensions extmanager, MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( master, shardno );

//        // Config - required by subjects.
//        if( Config.HYBSTER_ORDER_PREPARE_CERTIFICATES )
//            m_ordervariant = HybsterOrderVariant.FullProposalCertificateVotes;
//        else if( Config.USE_HASH_BASED_ORDERING )
//            m_ordervariant = HybsterOrderVariant.FullProposalHashedVotes;
//        else
//            m_ordervariant = HybsterOrderVariant.AgreeOnFullCommand;
//
//        m_ordercommitthreshold = Config.COMMITTED_THRESHOLD;
//        m_skipordermessages    = Config.SKIP_UNNECESSARY_ORDER_MESSAGES;
//
//        m_orderinstdist    = Config.ORDER_INSTANCE_DISTRIBUTION;
//        m_ordershardexec   = Config.ORDERSTAGE.getExecutionStageID( m_shardno );
//        m_orderwndsize     =  Config.ORDER_INSTANCES_WINDOW;
//        m_orderwndshard    = Config.ORDER_INSTANCES_WINDOW_STAGE;
//        m_actorderwnd      = Config.ACTIVE_ORDER_WINDOW;
//        m_actorderwndshard = Config.ACTIVE_ORDER_WINDOW_STAGE;
//        m_unboundorderwnd  = Config.UNBOUND_ORDER_WINDOW;
//        m_minbatchsize     = Config.MINIMUM_BATCH_SIZE;
//        m_maxbatchsize     = Config.MAXIMUM_BATCH_SIZE;
//        m_asyncchkpts      = Config.HYBSTER_ASYNCHONOUS_CHECKPOINTS;
//        m_chkptint         = Config.CHECKPOINT_INTERVAL;
//
//        // Members
//        m_msgstreams = new ReplicaMessageStream[ grpconf.getNumberOfReplicas() ];
//        Arrays.setAll( m_msgstreams, i -> new ReplicaMessageStream( (short) i ) );
//
//        // TODO: Get this from the view; if it is really needed.
//        m_repno = grpconf.getReplicaNumber();
//
//        // Shared
//        m_mapper = repprot.getMapperFactory().get();
//
//        m_tccons  = confac.createSequentialCounterGroupCertifier( 0 );
//        m_tmcons  = confac.createTrustedMacGroupCertifier();
//        m_clicons = confac.createReplicaToClientCertifiers();
//
//        m_reptctrans = new MessageTransmitter( m_mapper, m_tccons.getCertifier(), reptrans ); // Can only be used for broadcasts!
//        m_reptmtrans = new MessageTransmitter( m_mapper, m_tmcons.getCertifier(), reptrans );
//        m_reptrans   = new MessageTransmitter( m_mapper, null, null, reptrans );
//
//        // Subjects
//        m_orderhandler = new HybsterOrderShard( this, shardno, extmanager );
//        m_chkpthandler = new HybsterCheckpointShard( this, shardno, grpconf );
//        m_vwchghandler = new HybsterViewChangeShard( shardno );
        throw new  NotImplementedException();
    }


    @Override
    public String toString()
    {
        return String.format( "HYB%02d", m_shardno );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
        case HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID:
        case HybsterViewChangeMessages.HYBSTER_NEW_VIEW_ID:
        case HybsterViewChangeMessages.HYBSTER_SWITCH_VIEW_ID:
            handleTCMessage( (NetworkMessage) msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_CHECKPOINT_ID:
            if( ((NetworkMessage) msg).getSender()!=m_repno )
                handleTCMessage( (NetworkMessage) msg );
            else
            {
                // TODO: This should be directly delivered to the order handler.
//                assert !Config.HYBSTER_ASYNCHONOUS_CHECKPOINTS;
//                m_orderhandler.enqueueMessage( msg );
                throw new  NotImplementedException();
            }
            break;
        case InvocationMessages.REQUEST_ID:
            m_orderhandler.enqueueMessage( msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_SHARD_CHECKPOINT_STABLE_ID:
        case HybsterCheckpointMessages.HYBSTER_TC_VERIFICATION_ID:
        case CheckpointMessages.CHECKPOINT_CREATED_ID:
            m_chkpthandler.handleMessage( msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            m_orderhandler.enqueueMessage( msg );
            m_chkpthandler.handleMessage( msg );
            break;
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            m_orderhandler.handleMessage( msg );
            m_chkpthandler.handleMessage( msg );
            m_vwchghandler.handleMessage( msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }

        return false;
    }


    @Override
    public void initPeers(AgreementPeers config)
    {
        m_orderhandler.initPeers( config );
        m_chkpthandler.initPeers( config );
        m_vwchghandler.initPeers( config );
    }


    private void handleTCMessage(NetworkMessage msg)
    {
        ReplicaMessageStream stream = m_msgstreams[ msg.getSender() ];

        if( stream.enqueueMessage( msg ) )
        {
            while( stream.hasNext() )
                dispatchVerifiedMessage( stream.nextVerified() );
        }
    }


    private void dispatchVerifiedMessage(NetworkMessage msg)
    {
        switch( msg.getTypeID() )
        {
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
            m_orderhandler.enqueueMessage( msg );
            break;
        case HybsterCheckpointMessages.HYBSTER_CHECKPOINT_ID:
            m_chkpthandler.handleMessage( msg );
            break;
        case HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID:
        case HybsterViewChangeMessages.HYBSTER_NEW_VIEW_ID:
        case HybsterViewChangeMessages.HYBSTER_SWITCH_VIEW_ID:
            m_vwchghandler.handleMessage( msg );
            break;
        default:
            throw new IllegalStateException( msg.toString() );
        }
    }


    private class ReplicaMessageStream
    {
        private final short m_repno;

        private Map<Long, NetworkMessage> m_recvd = new HashMap<>();
        private long m_nextmsg = 1;
        private long m_lastpre = -1;
        private long m_lastcom = -1;
        private long m_lastchk = -1;

        public ReplicaMessageStream(short repno)
        {
            m_repno = repno;
        }

        public boolean enqueueMessage(NetworkMessage msg)
        {
            long cntval = m_tccons.getVerifier( msg.getSender() ).counterValue( msg.getCertificateData() );

            if( cntval>m_nextmsg )
                s_logger.warn( "{} received future message {}, cntval {}>{}", this, msg, cntval, m_nextmsg );
            else if( cntval<m_nextmsg )
            {
                s_logger.warn( "{} received an old message {}, cntval {}>{}", this, msg, cntval, m_nextmsg );
                return false;
            }

            // TODO: We would have to verify all the messages in that queue. Maybe someone else flooded it.
            if( m_recvd.size()==10_000 )
                throw new IllegalStateException( "Message queue full for " + m_recvd.values().iterator().next().getSender() );

            m_recvd.put( cntval, msg );

            return true;
        }

        public boolean hasNext()
        {
            return m_recvd.containsKey( m_nextmsg );
        }

        public NetworkMessage nextVerified()
        {
            NetworkMessage msg = m_recvd.remove( m_nextmsg );

            m_mapper.verifyMessage( msg, m_tccons );

            // Instances have to be performed in order. Messages with higher counter value but lower
            // order number are not permitted.
            if( msg.getTypeID()==HybsterOrderMessages.HYBSTER_PREPARE_ID )
            {
                long orderno = ((OrderNetworkMessage) msg).getOrderNumber();

                // TODO: It is actually required that orderno = last_prepare_from_anyone + 1, since no
                //       correct proposer is allowed to implicitly skip instances (okay, we could skip
                //       them here and every body would do so, but why should we do this?).
                //       Nevertheless, at this point, we can not just say orderno==m_lastpre+1 because
                //       there might have been view changes meanwhile and someone else ordered instances
                //       betweeen orderno and m_lastpre.
                if( orderno<=m_lastpre || orderno<=m_lastcom )
                    throw new UnsupportedOperationException();
                else
                    m_lastpre = orderno;
            }
            else if( msg.getTypeID()==HybsterOrderMessages.HYBSTER_COMMIT_ID )
            {
                long orderno = ((OrderNetworkMessage) msg).getOrderNumber();

                if( orderno<=m_lastcom )
                    throw new UnsupportedOperationException();
                else
                    m_lastcom = orderno;
            }
            else if( msg.getTypeID()==HybsterCheckpointMessages.HYBSTER_CHECKPOINT_ID )
            {
                long orderno = ((HybsterCheckpoint) msg).getOrderNumber();

                if( orderno<=m_lastchk )
                    throw new UnsupportedOperationException();
                else
                    m_lastchk = orderno;
            }

            // TODO: Check sequence of other protocol message as well.

            m_nextmsg++;

            return msg;
        }

        @Override
        public String toString()
        {
            return String.format( "HS%02d%d", m_shardno, m_repno );
        }
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    protected void executeSubjects()
    {
        if( m_orderhandler.isReady() )
           while( !m_orderhandler.execute() ) ;
    }


    @Override
    public MessageTransmitter getReplicaTCTransmitter()
    {
        return m_reptctrans;
    }


    @Override
    public MessageTransmitter getReplicaTMTransmitter()
    {
        return m_reptmtrans;
    }


    @Override
    public MessageTransmitter getReplicaTransmitter()
    {
        return m_reptrans;
    }


    @Override
    public VerifierGroup getReplicaTCVerifiers()
    {
        return m_tccons;
    }


    @Override
    public VerifierGroup getReplicaTMVerifiers()
    {
        return m_tmcons;
    }


    @Override
    public VerifierGroup getClientVerifiers()
    {
        return m_clicons;
    }


    @Override
    public MessageMapper getMessageMapper()
    {
        return m_mapper;
    }


    @Override
    public HybsterOrderVariant getOrderVariant()
    {
        return m_ordervariant;
    }


    @Override
    public int getOrderCommitThreshold()
    {
        return m_ordercommitthreshold;
    }


    @Override
    public boolean useSkipUnnecessaryOrderMessages()
    {
        return m_skipordermessages;
    }


    @Override
    public InstanceDistribution getOrderInstanceShardDistribution()
    {
        return m_orderinstdist;
    }


    @Override
    public int getExecutorNumberForOrderShard()
    {
        return m_ordershardexec;
    }


    @Override
    public int getOrderWindowSize()
    {
        return m_orderwndsize;
    }


    @Override
    public int getOrderWindowSizeForShard()
    {
        return m_orderwndshard;
    }


    @Override
    public int getActiveOrderWindowSize()
    {
        return m_actorderwnd;
    }


    @Override
    public int getActiveOrderWindowSizeForShard()
    {
        return m_actorderwndshard;
    }


    @Override
    public boolean useUnboundOrderWindow()
    {
        return m_unboundorderwnd;
    }


    @Override
    public int getMinumumCommandBatchSize()
    {
        return m_minbatchsize;
    }


    @Override
    public int getMaximumCommandBatchSize()
    {
        return m_maxbatchsize;
    }


    @Override
    public boolean useAsynchrounousCheckpoints()
    {
        return m_asyncchkpts;
    }


    @Override
    public int getCheckpointInterval()
    {
        return m_chkptint;
    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }

}
