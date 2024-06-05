package refit.pbfto.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import refit.pbfto.PBFTProtocolShard;
import refit.pbfto.order.PBFTOrderMessages.PBFTCommit;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrepare;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.view.View;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTOrderInstance extends OrderInstance
{

    private static enum InstanceState
    {
        INITIALIZED,
        PRE_PREPARED,
        PREPARED,
        COMMITTED
    }

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTOrderInstance.class );

    private final byte repno;
    private final PBFTOrderInstanceCertifier certificate;
    private InstanceState              instanceState;
    private short                      primaryID;
    private boolean                    isPrimary;
    private OrderMessages.CommandContainer           proposal;


    public PBFTOrderInstance(OrderInstanceContext cntxt, PBFTProtocolShard protshard, byte repno)
    {
        super( cntxt );
        this.repno = repno;
        this.certificate = new PBFTOrderInstanceCertifier( repno, false, protshard.getMessageMapper(),
                                                           protshard.getDefaultReplicaConnection(), protshard.getClientConnections()  );
    }


    // public REFITPBFTOrderInstance(REFITOrderSlot slot, OrderInstanceObserver protinstobserver,
    // View view, long instanceID, REFITProtocolMessage proposal) {
    // super(slot, protinstobserver);
    // this.certificate = new REFITPBFTOrderCertificate(slot);
    // init(view, instanceID);
    // this.proposal = proposal; // Must be after init()
    // }

    // ##################
    // # HELPER METHODS #
    // ##################

    @Override
    public String toString()
    {
        return "PBFT[" + instanceID + "]";
    }


    private void advanceState(InstanceState state)
    {
        s_logger.debug( "{} {}-{} reached {} ({}, {}) at {}", this, instanceID, view.getNumber(), state, proposal, certificate.message, cntxt );

        this.instanceState = state;
    }


    @Override
    public boolean isProposer()
    {
        return isPrimary && instanceState==InstanceState.INITIALIZED;
    }


    protected void orderReplicaMulticast(NetworkMessage message)
    {
        cntxt.broadcastToReplicas( message );
    }


    // protected REFITPBFTTransitionProtocolInstance createTransitionProtocolInstance(REFITMessage command) {
    // return new REFITPBFTTransitionProtocolInstance(slot, protinstobserver, instanceID, viewID + 1, command,
    // certificate.getProof());
    // }

    // ######################
    // # LIFE-CYCLE METHODS #
    // ######################

    @Override
    public void init(View view, long instanceID)
    {
        super.init( view, instanceID );
        certificate.init( instanceID, view.getNumber() );
//        primaryID = view.getProposer( instanceID );
        isPrimary = (repno == primaryID);
        proposal = null;
        advanceState( InstanceState.INITIALIZED );
    }


    public PBFTOrderInstanceCertifier abort()
    {
        abortInstance();

        return certificate;
    }


    public void advanceView(View view, PBFTPrePrepare preprep)
    {
        init( view, instanceID );

        if( preprep!=null )
        {
            assert preprep.getOrderNumber()==instanceID;

            proposal = preprep.getCommand();
            certificate.addPrePrepare( preprep );

            // TODO: There has to be a better way.
            if( isPrimary )
                advanceState( InstanceState.PRE_PREPARED );
            else
                sendPrepare();

            cntxt.instanceReady();
        }
    }

    // ######################
    // # PROTOCOL EXECUTION #
    // ######################

    @Override
    public boolean execute()
    {
        if( isPrimary )
            executePrimary();
        else
            executeBackup();

        return true;
    }


    private void executePrimary()
    {
        switch( instanceState )
        {
        case INITIALIZED:
            // Send PRE_PREPARE
            if( proposal == null )
            {
                proposal = fetchProposal();
                if( proposal == null )
                    break;
            }
            PBFTPrePrepare prePrepare = new PBFTPrePrepare( repno, instanceID, view.getNumber(), proposal );
            prePrepare.setValid();
            orderReplicaMulticast( prePrepare );

            // Add PRE_PREPARE to certificate
            certificate.addPrePrepare( prePrepare );

            // Advance to next state
            advanceState( InstanceState.PRE_PREPARED );
            //$FALL-THROUGH$
        case PRE_PREPARED:
            executePreparePhase();
            //$FALL-THROUGH$ --> Try to complete commit phase even if prepare phase has not been completed locally
        case PREPARED:
            executeCommitPhase();
            break;
        default:
            s_logger.warn( "{} nothing to do for state {}", this, instanceState );
        }
    }


    private void executeBackup()
    {
        switch( instanceState )
        {
        case INITIALIZED:
            while( true )
            {
                // Fetch PRE_PREPARE
                PBFTPrePrepare prePrepare = (PBFTPrePrepare) fetchMessage( PBFTOrderMessages.PBFT_PRE_PREPARE_ID,
                        view.getNumber() );

                if( prePrepare == null )
                    return;

                // Check whether PRE_PREPARE is from primary
//                if( prePrepare.getSender() != view.getProposer( instanceID ) )
//                {
//                    s_logger.warn( "{} bad PRE_PREPARE {}", this, prePrepare );
//                    continue;
//                }

                // Check whether the proposed message matches the proposal expected (if there is such a proposal)
                // TODO: It should be save to accept non-no-op proposal if the expected proposal is a no-op
                if( (proposal != null) && !proposal.equals( prePrepare.getCommand() ) )
                {
                    s_logger.warn( "{} bad PRE_PREPARE {} (message mismatch: {} expected)", prePrepare, proposal );
                    continue;
                }

                // Add PRE_PREPARE to certificate
                boolean success = certificate.addPrePrepare( prePrepare );
                if( !success )
                {
                    s_logger.warn( "{} bad PRE_PREPARE {}", this, prePrepare );
                    continue;
                }
                break;
            }

            sendPrepare();
            //$FALL-THROUGH$ --> Try to also complete prepare phase
        case PRE_PREPARED:
            executePreparePhase();
            //$FALL-THROUGH$ --> Try to complete commit phase (even if prepare phase has not been completed locally)
        case PREPARED:
            executeCommitPhase();
            break;
        default:
            s_logger.warn( "{} nothing to do for state {}", this, instanceState );
        }
    }

    private void sendPrepare()
    {
        // Create and distribute PREPARE
//        PBFTPrepare ownPrepare = Config.USE_HASH_BASED_ORDERING ?
//                new PBFTPrepare( repno, instanceID, view.getNumber(), certificate.prePrepare.getCommand().getMessageDigest() ) :
//                new PBFTPrepare( repno, instanceID, view.getNumber(), certificate.prePrepare.getCommand() );
//        ownPrepare.setValid();
//
//        orderReplicaMulticast( ownPrepare );
//
//        // Add PREPARE to certificate
//        certificate.addPrepare( ownPrepare );
//
//        // Advance to next state
//        advanceState( InstanceState.PRE_PREPARED );
        throw new  NotImplementedException();
    }

    private void executePreparePhase()
    {
        while( !certificate.isPrepared() )
        {
            // Fetch PREPAREs
            PBFTPrepare prepare = (PBFTPrepare) fetchMessage( PBFTOrderMessages.PBFT_PREPARE_ID, view.getNumber() );
            if( prepare == null )
                return;

            // Add PREPARE to certificate
            boolean success = certificate.addPrepare( prepare );
            if( !success )
                s_logger.warn( "{} bad PREPARE {}", this, prepare );
        }

        // Create and distribute COMMIT
//        PBFTCommit ownCommit = Config.USE_HASH_BASED_ORDERING ?
//                new PBFTCommit( repno, instanceID, view.getNumber(), certificate.prePrepare.getCommand().getMessageDigest() ) :
//                new PBFTCommit( repno, instanceID, view.getNumber(), certificate.prePrepare.getCommand() );
//        ownCommit.setValid();
//
//        orderReplicaMulticast( ownCommit );
//
//        // Add COMMIT to certificate
//        certificate.addCommit( ownCommit );
//
//        // Advance to next state
//        advanceState( InstanceState.PREPARED );
        throw new  NotImplementedException();
    }


    private void executeCommitPhase()
    {
        while( !certificate.isCommitted() )
        {
            // Fetch COMMITs
            PBFTCommit commit = (PBFTCommit) fetchMessage( PBFTOrderMessages.PBFT_COMMIT_ID, view.getNumber() );
            if( commit == null )
                return;

            // Add COMMIT to certificate
            boolean success = certificate.addCommit( commit );
            if( !success )
                s_logger.warn( "{} bad COMMIT {}", this, commit );
        }

        // Advance to next state
        advanceState( InstanceState.COMMITTED );

        // Instance complete
        complete( primaryID, certificate.message );
    }

}
