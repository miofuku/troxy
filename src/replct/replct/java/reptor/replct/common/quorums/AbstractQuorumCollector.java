package reptor.replct.common.quorums;

import java.util.Arrays;

import reptor.chronos.Orphic;
import reptor.distrbt.com.VerificationException;
import reptor.jlib.collect.Slots;


public abstract class AbstractQuorumCollector<M, V> implements Orphic
{

    private final Slots<M> m_msgstore;
    private final Slots<M> m_proposals;
    private final Votes<V> m_votes;
    private final int[]    m_voter_to_vote;

    // TODO: Threshold is actually policy. For instance, it could be required to wait for x machines out of at least y.
    private int m_threshold;


    public AbstractQuorumCollector(int nvoters, int threshold, int maxprops)
    {
        m_threshold     = threshold;
        m_msgstore      = new Slots<>( nvoters );
        m_proposals     = new Slots<>( maxprops );
        m_votes         = new Votes<>( maxprops );
        m_voter_to_vote = new int[ nvoters ];

        clear();
    }


    public int getNumberOfVoters()
    {
        return m_msgstore.capacity();
    }


    public int getNumberOfVotes()
    {
        return m_msgstore.size();
    }


    public int getThreshold()
    {
        return m_threshold;
    }


    public void setThreshold(int value)
    {
        m_threshold = value;
    }


    public Slots<M> getMessages()
    {
        return m_msgstore;
    }


    public Slots<M> getProposals()
    {
        return m_proposals;
    }


    public Votes<V> getVotes()
    {
        return m_votes;
    }


    public boolean isQuorumStable()
    {
        return m_votes.getLeadingCount()>=m_threshold && m_proposals.containsKey( m_votes.getLeadingClass() );
    }


    public boolean isQuorumPossible()
    {
        return m_threshold-m_votes.getLeadingCount() <= m_msgstore.emptySlotsCount();
    }


    public M getLeadingProposal()
    {
        assert m_msgstore.size()>0;

        return m_proposals.get( m_votes.getLeadingClass() );
    }


    protected boolean isAlreadyKnownInternal(int voter, M msg) throws VerificationException
    {
        M curmsg = m_msgstore.get( voter );

        return MessageCollectors.isMessageAlreadyKnown( msg, curmsg );
    }


    protected boolean addVoteInternal(int voter, M msg, V vote, boolean isprop)
    {
        assert !m_msgstore.containsKey( voter );

        m_msgstore.put( voter, msg );

        int vc = m_votes.addVote( vote );

        m_voter_to_vote[ voter ] = vc;

        if( isprop && !m_proposals.containsKey( vc ) )
            m_proposals.put( vc, msg );

        return isQuorumStable() || !isQuorumPossible();
    }


    public void clear()
    {
        m_msgstore.clear();
        m_proposals.clear();
        m_votes.clear();

        Arrays.fill( m_voter_to_vote, -1 );
    }

}
