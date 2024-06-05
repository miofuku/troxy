package reptor.replct.agree.checkpoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ImmutableObject;
import reptor.chronos.Orphic;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.link.SelectorLink;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.common.data.Data;
import reptor.jlib.collect.Slots;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolShard;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.ViewLogging;
import reptor.replct.agree.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.checkpoint.CheckpointMessages.CheckpointCreated;
import reptor.replct.agree.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.common.quorums.Votes;


// TODO: Process checkpoint messages in reverse order using an actor interface.
public class OnePhaseCheckpointShard implements ProtocolShard, MessageHandler<Message>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface CheckpointShardContext
    {
        ChronosDomainContext getDomainContext();

        MessageMapper                                       getMessageMapper();
        PushMessageSink<? super CheckpointNetworkMessage>   getReplicaChannel();
    }


    // TODO: Serialise and deserialise methods.
    public static class CheckpointCertificate implements ImmutableObject
    {
        private final Checkpoint[] m_chkpts;

        public CheckpointCertificate(Checkpoint[] chkpts)
        {
            Preconditions.checkArgument( chkpts.length>0 );

            m_chkpts = chkpts;
        }

        public long getOrderNumber()
        {
            return m_chkpts[ 0 ].getOrderNumber();
        }

        public Checkpoint[] getCheckpoints()
        {
            return m_chkpts;
        }
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( OnePhaseCheckpointShard.class );

    private final Checkpointing             m_config;
    private final ChronosDomainContext      m_cntxt;
    private final short                     m_shardno;

    private final MessageMapper             m_mapper;
    private final GroupConnectionCertifier  m_certif;
    private final PushMessageSink<? super CheckpointNetworkMessage> m_repconn;

    private final int                       m_chkptquorum;
    private final CheckpointMode            m_chkptmode;
    private final boolean                   m_hashedchkpts;
    private final boolean                   m_passiveprogress;

    // TODO: Use preallocated instances and order window.
    private final Map<Long, Instance>       m_instances     = new HashMap<>();
    private long                            m_stablechkptno = -1;

    // Configuration dependent
    private PushMessageSink<Message>           m_snapshotlearner;
    private PushMessageSink<CheckpointStable>  m_chkptlearner;

    // View dependent
    private View            m_stableview = null;
    private int             m_curviewno  = -1;

    // Group dependent
    private byte            m_repno     = -1;
    private byte            m_nreplicas = -1;
    private int             m_maxvotes  = -1;


    public OnePhaseCheckpointShard(short shardno, CheckpointShardContext cntxt, GroupConnectionCertifier certif,
                                   Checkpointing config)
    {
        m_config  = config;
        m_cntxt   = cntxt.getDomainContext();
        m_shardno = shardno;
        m_mapper  = cntxt.getMessageMapper();
        m_certif  = certif;
        m_repconn = cntxt.getReplicaChannel();

        m_chkptquorum     = config.getCheckpointQuorumSize();
        m_chkptmode       = config.getCheckpointMode();
        m_hashedchkpts    = config.useHashedCheckpoints();
        m_passiveprogress = config.usePassiveProgress();
    }


    private ChronosAddress domainAddress()
    {
        return m_cntxt.getDomainAddress();
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

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


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initPeers(AgreementPeers peers)
    {
        m_snapshotlearner = createSnapshopLearner( peers );
        m_chkptlearner    = createCheckpointLearner( peers );
    }


    public void initView(View view)
    {
        s_logger.debug( ViewLogging.MARKER, "{} switch to view {}", this, view.getNumber() );

        m_stableview = view;
        m_curviewno  = view.getNumber();

        // The first view change is taken as a change of the group composition.
        if( m_curviewno==1 )
        {
            m_repno     = m_stableview.getReplicaNumber();
            m_nreplicas = m_stableview.getNumberOfReplicas();
            m_maxvotes  = m_stableview.getNumberOfTolerableFaults()+1;
        }
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case CheckpointMessages.CHECKPOINT_ID:
            handleCheckpoint( (Checkpoint) msg );
            break;
        case CheckpointMessages.CHECKPOINT_STABLE_ID:
            handleStableCheckpoint( (CheckpointStable) msg );
            break;
        case CheckpointMessages.CHECKPOINT_CREATED_ID:
            handleInternalCheckpoint( (CheckpointCreated) msg );
            break;
        default:
            throw new IllegalArgumentException( msg.toString() );
        }

        // TODO: What is the external state? The last stable checkpoint, view, configuration? Everything?
        return false;
    }


    //-------------------------------------//
    //             Checkpoint              //
    //-------------------------------------//

    private class Instance implements Orphic
    {
        private final Slots<Checkpoint> m_msgstore;
        private final Votes<Data>       m_votes;
        private boolean                 m_hasown;
        private boolean                 m_hasfull;

        public Instance()
        {
            m_msgstore = new Slots<>( m_nreplicas );
            m_votes    = new Votes<Data>( m_maxvotes );
            m_hasown   = false;
            m_hasfull  = false;
        }

        public boolean handleCheckpoint(Checkpoint chkpt)
        {
            if( !isAlreadyKnown( chkpt ) )
            {
                m_msgstore.put( chkpt.getSender(), chkpt );

                if( chkpt.getSender()==m_repno )
                    m_hasown = true;

                if( chkpt.isFullCheckpoint() )
                    m_hasfull = true;

                if( chkpt.getStateHash()==null )
                    chkpt.prepareDigestion( m_mapper );

                m_votes.addVote( chkpt.getStateHash() );
                assert m_votes.isUnanimous();
            }

            return hasCertificate();
        }

        public boolean hasCheckpointReached()
        {
            return m_hasown;
        }

        public boolean hasFullCheckpoint()
        {
            return m_hasfull;
        }

        public boolean hasCertificate()
        {
            return m_votes.getLeadingCount()>=m_chkptquorum;
        }

        public CheckpointCertificate certificate()
        {
            return new CheckpointCertificate( m_msgstore.toArray( Checkpoint[]::new ) );
        }

        // TODO: This should be shared; see order instances.
        private boolean isAlreadyKnown(NetworkMessage msg)
        {
            NetworkMessage curmsg = m_msgstore.get( msg.getSender() );

            if( curmsg==null )
                return false;
            else if( curmsg.equals( msg ) )
                return true;
            else
                // TODO: Did someone send two conflicting messages? -> blacklist.
                throw new UnsupportedOperationException();
        }
    }


    private void handleInternalCheckpoint(CheckpointCreated intchkpt)
    {
        if( !m_chkptmode.includes( CheckpointMode.SEND ) )
            return;

        long orderno = intchkpt.getOrderNumber();

        if( orderno<=m_stablechkptno )
            return;

        assert !m_hashedchkpts;

        Instance inst = instance( orderno );

        if( m_passiveprogress && inst.hasCertificate() )
            distributeStableCheckpoint( orderno, inst.certificate() );
        else
        {
            Checkpoint ownchkpt = new Checkpoint( m_repno, (short) 0, m_curviewno, intchkpt.getOrderNumber(),
                                                  intchkpt.getServiceState(), intchkpt.getResultMap(),
                                                  intchkpt.isFullServiceState() );
            ownchkpt.setValid();

            sendCheckpoint( ownchkpt );

            if( inst.handleCheckpoint( ownchkpt ) )
                distributeStableCheckpoint( orderno, inst.certificate() );
        }
    }


    private void sendCheckpoint(Checkpoint ownchkpt)
    {
        s_logger.debug( "{} send own checkpoint {}", this, ownchkpt );

        // This creates also the state hash.
        m_mapper.certifyAndSerializeMessage( ownchkpt, m_certif.getCertifier() );
        m_repconn.enqueueMessage( ownchkpt );
    }


    private void handleCheckpoint(Checkpoint chkpt)
    {
        s_logger.debug( "{} received checkpoint {}", this, chkpt );

        long orderno = chkpt.getOrderNumber();

        // Do not process old checkpoints
        if( orderno<=m_stablechkptno )
            return;

        m_mapper.verifyMessage( chkpt, m_certif );

        Instance inst = instance( orderno );

        if( inst.handleCheckpoint( chkpt ) )
        {
            if( inst.hasCheckpointReached() )
                distributeStableCheckpoint( orderno, inst.certificate() );
            else if( m_snapshotlearner!=null && inst.hasFullCheckpoint() )
            {
                Checkpoint ck = inst.certificate().getCheckpoints()[ 0 ];

                m_snapshotlearner.enqueueMessage( new Snapshot( orderno, ck.getServiceState(), ck.getResultMap() ) );

                distributeStableCheckpoint( orderno, inst.certificate() );
            }
        }
    }


    private void distributeStableCheckpoint(long orderno, CheckpointCertificate cert)
    {
        // Distribute progress notification
        CheckpointStable stablechkpt = new CheckpointStable( orderno );

        // Create and forward checkpoint operation
        m_chkptlearner.enqueueMessage( stablechkpt );

        checkpointStable( orderno );
    }


    private void handleStableCheckpoint(CheckpointStable stabchkpt)
    {
        if( stabchkpt.getOrderNumber()<=m_stablechkptno )
            return;

        checkpointStable( stabchkpt.getOrderNumber() );
    }


    private void checkpointStable(long orderno)
    {
        assert m_stablechkptno<orderno;

        m_stablechkptno = orderno;

        m_instances.keySet().removeIf( x -> x<=m_stablechkptno );

        s_logger.debug( "{} stable checkpoint for order number {}, running {}", this, orderno, m_instances.size() );
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private Instance instance(long orderno)
    {
        Instance inst = m_instances.get( orderno );

        if( inst==null )
            m_instances.put( orderno, inst = new Instance() );

        return inst;
    }


    private PushMessageSink<Message> createSnapshopLearner(AgreementPeers peers)
    {
        if( !m_chkptmode.includes( CheckpointMode.APPLY ) )
            return null;
        else if( !m_config.getPropogateToAll() )
            return peers.getExecutors().get( m_config.getLinkedExecutorForCheckpointShard( m_shardno ) ).createChannel( domainAddress() );
        else
            return peers.createMulticastChannel( peers.getExecutors(), domainAddress() );
    }


    private PushMessageSink<CheckpointStable> createCheckpointLearner(AgreementPeers peers)
    {
        if( !m_config.getPropogateToAll() )
        {
            Function<CheckpointStable, Integer> selector =
                    msg -> peers.getInternalOrderCoordinator( msg.getOrderNumber() );

            return new SelectorLink<>( peers.createChannels( peers.getOrderShards(), domainAddress() ), selector );
        }
        else
        {
            // TODO: The coordinator should not be included.
            Set<DomainEndpoint<PushMessageSink<Message>>> recset = new HashSet<>();
            recset.addAll( peers.getOrderShards() );
            recset.addAll( peers.getCheckpointShards() );

            return peers.createMulticastChannel( recset, domainAddress() );
        }
    }

}
