package reptor.replct.invoke.bft;

import reptor.chronos.Orphic;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.ReplicaGroup;
import reptor.replct.common.quorums.SenderQuorumCollector;
import reptor.replct.invoke.InvocationMessages.Reply;


public class BFTReplyPhaseClient implements Orphic
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final MessageMapper                         m_mapper;
    private final VerifierGroup                         m_repverif;
    private final boolean                               m_hashedreplies;
    private final SenderQuorumCollector<Reply, Data>    m_replies;

    private boolean                             m_iscomplete = false;

    private Reply                                       fastRead = null;
    private Integer                                     fastReadHash = 0;
    private int                                         count = 0;


    public BFTReplyPhaseClient(MessageMapper mapper, ReplicaGroup repgroup, VerifierGroup repverif, BFTInvocation invoke)
    {
        m_mapper        = mapper;
        m_repverif      = repverif;
        m_hashedreplies = invoke.getReplyModeStrategy().getUseHashedReplies();

        short nreplicas = repgroup.size();
        short nfaults   = repgroup.getNumberOfTolerableFaults();

        m_replies = new SenderQuorumCollector<>( nreplicas, invoke.writeReplyQuorumSize( nreplicas, nfaults ),
                                                 invoke.maximumNumberOfResults( nreplicas, nfaults ) );
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    public int getNumberOfDifferingResults()
    {
        return m_replies.getVotes().getNumberOfClasses();
    }


    public boolean isComplete()
    {
        return m_iscomplete;
    }


    public ImmutableData getResult()
    {
        assert isComplete();

        return m_replies.isQuorumStable() ? m_replies.getLeadingProposal().getResult() : null;
    }


    // TODO: The contact replica could be forged since there is no agreement about it.
    //       Possible solution: Use a separate protocol to obtain the view.
    //       However, using 3f+1 it would be possible to collect a quorum and take the median.
    //       With 2f+1, this wouldn't be possible. There, some quorum certificate has to prove the correctness
    //       of an alleged view.
    public byte getCurrentContactReplica()
    {
        assert isComplete() && m_replies.isQuorumStable();

        return m_replies.getLeadingProposal().getContactReplica();
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initInvocation()
    {
        m_replies.clear();
        m_iscomplete = false;
        fastRead = null;
        fastReadHash = 0;
        count = 0;
    }


    public void initInvocation(int replyqs)
    {
        initInvocation();
        m_replies.setThreshold( replyqs );
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    public boolean handleReply(Reply reply)
    {
        // If we had time, we could nevertheless verify this REPLY to detect errors.
        if( isComplete() )
            return false;

        if( m_replies.isAlreadyKnown( reply ) )
            return false;

        assert reply.isCertificateValid()!=Boolean.FALSE;

        if( reply.isCertificateValid()==null )
        {
            m_mapper.verifyMessage( reply, m_repverif );
            reply.setInnerMessagesValid( true );
        }

        if( !m_replies.addVote( reply, voteForReply( reply ), reply.isFullResult() ) )
            return false;
        else
        {
            phaseCompleted();
            return true;
        }
    }


    private void phaseCompleted()
    {
        m_iscomplete = true;
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private ImmutableData voteForReply(Reply reply)
    {
        if( !m_hashedreplies )
            return reply.getResult();
        else if( !reply.isFullResult() )
            return reply.getResult(); // Contains a hash.
        else
        {
            if( reply.getResultHash()==null )
                reply.prepareDigestion( m_mapper );

            return reply.getResultHash();
        }
    }


    //-------------------------------------//
    //           Fast-read Vote            //
    //-------------------------------------//

    public boolean handleFastRead(Reply reply, short repno)
    {
        count++;

        if( isComplete() )
            return false;

        assert reply.isCertificateValid()!=Boolean.FALSE;

        if( reply.isCertificateValid()==null )
        {
            m_mapper.verifyMessage( reply, m_repverif );
            reply.setInnerMessagesValid( true );
        }

        if (reply.getSender() == repno) // reply
            fastRead = reply;
        else if (reply.getResult().size()==4) // cache
            fastReadHash = reply.getResult().byteBuffer().getInt();
        else
            count--;

        if( fastRead == null || fastReadHash == 0)
        {
            return false;
        }
        else
        {
            if (fastReadHash != fastRead.getResult().hashCode())
            {
                phaseCompleted();
                return false;
            }
            else
            {
                phaseCompleted();
                return true;
            }
        }
    }


    public int getReads()
    {
        return count;
    }


    public Reply getFastReadReply()
    {
        return fastRead;
    }
}
