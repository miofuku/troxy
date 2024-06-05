package refit.pbfto.order;

import refit.common.BallotBox;
import refit.pbfto.order.PBFTOrderMessages.PBFTCommit;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrepare;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.common.data.Data;
import reptor.replct.agree.common.order.AbstractOrderHolderMessage;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTOrderInstanceCertifier
{
    private final byte repno;
    private long                                              instanceID;
    private int                                               viewID;

    public PBFTPrePrepare                                     prePrepare;
    public OrderMessages.CommandContainer                                    message;
    private final BallotBox<Short, Data, AbstractOrderHolderMessage> prepares;
    private final BallotBox<Short, Data, PBFTCommit>       commits;
    private final MessageMapper    m_mapper;
    private final VerifierGroup m_defverif;
    private final VerifierGroup m_cliverif;


    public PBFTOrderInstanceCertifier(byte repno, boolean verifyonly, MessageMapper mapper,
            VerifierGroup defverif, VerifierGroup cliverif)
    {
        // TODO: For the verification, it does not matter if we have PREPAREs or COMMITs, does it?
        //       Castro '99 says, only 1 PREPREPARE and 2f PREPAREs. But what if we learn about a commit
        //       via 2f+1 COMMITs?
        // TODO: verifyonly ? Config.WEAK_QUORUM .. does not work with authenticators, does it?
//        this.repno = repno;
//        int prethres  = Config.PREPARED_THRESHOLD;
//        this.prepares = new BallotBox<Short, Data, AbstractOrderHolderMessage>( prethres );
//        int comthres  = Config.COMMITTED_THRESHOLD;
//        this.commits  = new BallotBox<Short, Data, PBFTCommit>( comthres );
//        m_mapper = mapper;
//        m_defverif  = defverif;
//        m_cliverif  = cliverif;
        throw new  NotImplementedException();
    }


    @Override
    public String toString()
    {
        return "{" + viewID + " " + instanceID + ": " + isPrepared() + " " + isCommitted() + "}";
    }


    public void init(long instanceID, int viewID)
    {
        this.instanceID = instanceID;
        this.viewID = viewID;
        this.prePrepare = null;
        this.message = null;
        this.prepared = false;
        this.committed = false;
        prepares.clear();
        commits.clear();
    }


    // ###################
    // # ADDING MESSAGES #
    // ###################

    private boolean matchesInstance(OrderNetworkMessage msg)
    {
        return msg!=null && msg.getViewNumber()==viewID && msg.getOrderNumber()==instanceID;
    }

    private void verifyMessage(OrderNetworkMessage msg)
    {
        // TODO: How do we verify own messages when we receive them in view changes?
        //       Do we have to compare it with our local copy?
        //       (Or should we recalculate the authenticator?)
        if( msg.getSender()!=repno )
            m_mapper.verifyMessage( msg, m_defverif.getVerifier( msg.getSender() ) );
    }

    public boolean addPrePrepare(PBFTPrePrepare prePrepare)
    {
        if( !matchesInstance( prePrepare ) )
            throw new UnsupportedOperationException();
        if( this.prePrepare != null )
            throw new UnsupportedOperationException();
        if( message != null )
            throw new UnsupportedOperationException();

        verifyMessage( prePrepare );

        prePrepare.getCommand().verifyCommands( m_mapper, m_cliverif );

        message = prePrepare.getCommand();
        this.prePrepare = prePrepare;

        Data vote = getMessageDigest( prePrepare.getCommand() );
        prepares.add( prePrepare.getSender(), vote, prePrepare );

        checkPrepared();
        return true;
    }


    public boolean addPrepare(PBFTPrepare prepare)
    {
        if( !matchesInstance( prepare ) )
            throw new UnsupportedOperationException();
        if( prepares.hasVoted( prepare.getSender() ) )
            throw new UnsupportedOperationException();

        verifyMessage( prepare );

//        Data vote = Config.USE_HASH_BASED_ORDERING ? prepare.getData() :
//                                                     getMessageDigest( prepare.getCommand() );
//        prepares.add( prepare.getSender(), vote, prepare );
//        checkPrepared();
//        return true;
        throw new  NotImplementedException();
    }


    public boolean addCommit(PBFTCommit commit)
    {
        if( !matchesInstance( commit ) )
            throw new UnsupportedOperationException();
        if( commits.hasVoted( commit.getSender() ) )
            throw new UnsupportedOperationException();

        verifyMessage( commit );

//        Data vote = Config.USE_HASH_BASED_ORDERING ? commit.getData() :
//                                                     getMessageDigest( commit.getCommand() );
//        commits.add( commit.getSender(), vote, commit );
//        checkCommitted();
//        return true;
        throw new  NotImplementedException();
    }


    private Data getMessageDigest(NetworkMessage msg)
    {
        Data digest = msg.getMessageDigest();

        // TODO: Should only happen in Debug mode or when the verifier does not use message digests.
        if( digest==null )
            digest = m_mapper.digestMessage( msg ).getMessageDigest();

        return digest;
    }


    // ######################
    // # CERTIFICATE STATES #
    // ######################

    private boolean prepared;
    private boolean committed;


    public boolean isPrepared()
    {
        return prepared;
    }


    public boolean isCommitted()
    {
        return committed;
    }


    private void checkPrepared()
    {
        if( prepared )
            return;
        if( prePrepare == null )
            return;
        Data decision = prepares.getDecision();
        if( decision == null )
            return;
        if( !prepares.getDecidingBallots().contains( prePrepare ) )
            return;
        prepared = true;
    }


    private void checkCommitted()
    {
        if( committed )
            return;
        if( !prepared )
            return;
        if( prePrepare == null )
            return;

        Data decision = commits.getDecision();
        if( decision == null )
            return;
        // In the current implementation, a non-faulty replica that
        // has been provided with a faulty PRE_PREPARE will never
        // reach the status "committed" in the corresponding instance,
        // even if it is able to obtain 2f+1 correct COMMITs; see the
        // following code line. This is not a safety problem but
        // implementing a solution (which would require obtaining
        // the correct message) would lead to a more robust protocol.
        if( !decision.equals( prePrepare.getCommand().getMessageDigest() ) )
            throw new UnsupportedOperationException();
        committed = true;
    }


    // ##################
    // # PROGRESS PROOF #
    // ##################

    public OrderNetworkMessage[] getProof()
    {
//        if( isPrepared() )
//            return createProof( prepares, Config.PREPARED_THRESHOLD );
//        return null;
        throw new  NotImplementedException();
    }


    private OrderNetworkMessage[] createProof(BallotBox<?, ?, ? extends OrderNetworkMessage> ballotBox, int proofSize)
    {
        assert proofSize>1;

        OrderNetworkMessage[] proof = new OrderNetworkMessage[proofSize];

        proof[ 0 ] = prePrepare;

        int i = 1;
        for( OrderNetworkMessage message : ballotBox.getDecidingBallots() )
        {
            if( message==prePrepare )
                continue;

            proof[i] = message;

            if( ++i==proofSize )
                break;
        }

        return proof;
    }

}
