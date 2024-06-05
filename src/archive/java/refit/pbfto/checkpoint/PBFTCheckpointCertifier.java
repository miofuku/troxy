package refit.pbfto.checkpoint;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import refit.common.BallotBox;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.CheckpointCreated;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTCheckpointCertifier
{

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTCheckpointCertifier.class );

    public final long                                                    agreementSeqNr;
    public final BallotBox<Short, CheckpointCertificateVote, Checkpoint> checkpoints;
    public final CheckpointCreated                           partchks[];
    public int                                                           nr_part_chks;
    public int                                                           total_chkpt_size;

    private boolean                                                      hasown = false;
    private final MessageMapper mapper;
    private final GroupConnectionCertifier                                       auth;

    public PBFTCheckpointCertifier(long agreementSeqNr, MessageMapper mapper, GroupConnectionCertifier auth)
    {
//        this.mapper = mapper;
//        this.agreementSeqNr = agreementSeqNr;
//        this.checkpoints = new BallotBox<Short, CheckpointCertificateVote, Checkpoint>(
//                Config.REGULAR_CHECKPOINT_STABILITY_THRESHOLD );
//        this.auth = auth;
//
//        partchks = Config.EXECUTIONSTAGE.getNumber() > 1 && Config.CHECKPOINTSTAGE.propagateToAll() ?
//                new CheckpointCreated[Config.EXECUTIONSTAGE.getNumber()] : null;
        throw new  NotImplementedException();
    }


    public void add(CheckpointCreated partchk)
    {
        partchks[partchk.getPartitionNumber()] = partchk;
        total_chkpt_size += partchk.getServiceState().size();
        nr_part_chks++;
    }


    public boolean add(Checkpoint checkpoint, boolean isown)
    {
        if( checkpoint == null )
            return false;
        if( agreementSeqNr != checkpoint.getOrderNumber() )
            return false;

        if( !isown )
            mapper.verifyMessage( checkpoint, auth.getVerifier( checkpoint.getSender() ) );

        hasown = hasown || isown;
        ImmutableData vote = checkpoint.getServiceState()==null ? ImmutableData.EMPTY : checkpoint.getServiceState();
        return checkpoints.add( checkpoint.getSender(), new CheckpointCertificateVote( vote,
                checkpoint.getResultMap() ), checkpoint );
    }


    public boolean isStable()
    {
        // -- If checkpoints are not applied, it has to be ensured that the execution stage of this replica
        // -- has already passed the checkpoint. Otherwise the execution stage would not be able to learn
        // -- the result of an instance if the order stage was instructed to move its instance window forward
        // -- and consequently to skip older, even locally uncompleted instances.
//        if( !Config.CHECKPOINT_MODE.includes( CheckpointMode.APPLY ) && !hasown )
//            return false;
//        CheckpointCertificateVote decision = checkpoints.getDecision();
//        if( decision == null )
//            return false;
//        return true;
        throw new  NotImplementedException();
    }


    public Checkpoint getStableCheckpoint()
    {
        // Check whether the checkpoint has become stable
        if( !isStable() )
            return null;

        // Return a full checkpoint if available
        List<Checkpoint> correctCheckpoints = checkpoints.getDecidingBallots();
        for( Checkpoint checkpoint : correctCheckpoints )
        {
            if( checkpoint.isFullCheckpoint() )
                return checkpoint;
        }

        // No full checkpoint available -> Return a correct hash checkpoint
        return correctCheckpoints.get( 0 );
    }


    // ######################
    // # CHECKPOINT CONTENT #
    // ######################

    private static class CheckpointCertificateVote
    {

        private final ImmutableData hash;
        private final long[] nodeProgress;


        public CheckpointCertificateVote(ImmutableData hash, long[] nodeProgress)
        {
            this.hash = hash;
            this.nodeProgress = nodeProgress;
        }


        @Override
        public boolean equals(Object object)
        {
//            if( Config.ENABLE_DEBUG_CHECKS )
//            {
//                if( object == null )
//                    return false;
//                if( !(object instanceof CheckpointCertificateVote) )
//                    return false;
//            }
            CheckpointCertificateVote other = (CheckpointCertificateVote) object;
            if( !hash.equals( other.hash ) )
                return false;

            if( (nodeProgress == null) != (other.nodeProgress == null) )
                return false;

            if( nodeProgress != null )
            {
                for( int i = 0; i < nodeProgress.length; i++ )
                {
                    if( nodeProgress[i] != other.nodeProgress[i] )
                    {
                        s_logger.warn( "client-progress mismatch @ {}: {} vs {}", i, nodeProgress[i], other.nodeProgress[i] );
                        return false;
                    }
                }
            }
            return true;
        }


        @Override
        public int hashCode()
        {
            return hash.hashCode();
        }

    }

}
