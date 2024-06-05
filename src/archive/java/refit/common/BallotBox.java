package refit.common;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Deprecated
public class BallotBox<I, V, B> extends AbstractMap<I, B> implements Map<I, B>
{

    private final int             decisionThreshold;
    private final Map<I, B>       ids;
    private final Map<V, List<B>> votes;
    private V                     maxvote;


    public BallotBox(int decisionThreshold)
    {
        this.decisionThreshold = decisionThreshold;
        this.ids = new HashMap<I, B>();
        this.votes = new HashMap<V, List<B>>();
        this.maxvote = null;
    }


    @Override
    public String toString()
    {
        return "{" + ids.keySet() + ", " + votes + "}";
    }


    @Override
    public int size()
    {
        return ids.size();
    }


    @Override
    public boolean containsKey(Object key)
    {
        return ids.containsKey( key );
    }


    @Override
    public B get(Object key)
    {
        return ids.get( key );
    }


    @Override
    public void clear()
    {
        ids.clear();
        votes.clear();
        maxvote = null;
    }


    @Override
    public Set<Map.Entry<I, B>> entrySet()
    {
        return ids.entrySet();
    }


    public int getVoteCount()
    {
        return size();
    }


    public boolean hasVoted(I id)
    {
        return containsKey( id );
    }


    public B getVote(I id)
    {
        return get( id );
    }


    public boolean add(I id, V vote, B ballot)
    {
        Objects.requireNonNull( vote );

        // Check whether 'id' has already cast a vote
        if( ids.containsKey( id ) )
            return false;

        // Mark that 'id' has cast a vote
        ids.put( id, ballot );

        // Count vote
        List<B> ballots = votes.get( vote );
        if( ballots == null )
        {
            ballots = new ArrayList<B>( decisionThreshold );
            votes.put( vote, ballots );
        }
        ballots.add( ballot );

        if( maxvote==null || !maxvote.equals( vote ) && votes.get( maxvote ).size()<ballots.size() )
            maxvote = vote;

        return true;
    }


    public int getCandidateCount()
    {
        return maxvote==null ? 0 : votes.get( maxvote ).size();
    }


    public V getDecision()
    {
        return ids.size()>=decisionThreshold && votes.get( maxvote ).size()>=decisionThreshold ? maxvote : null;

    }


    public List<B> getDecidingBallots()
    {
        V v = getDecision();

        return v!=null ? votes.get( v ) : null;
    }


    public List<B> getBallots()
    {
        List<B> ballots = new LinkedList<B>();
        for( List<B> ballotList : votes.values() )
            ballots.addAll( ballotList );
        return ballots;
    }


    public List<B> getBallots(V vote)
    {
        return votes.get( vote );
    }


    public boolean votesDiffer()
    {
        return votes.keySet().size() > 1;
    }

}
