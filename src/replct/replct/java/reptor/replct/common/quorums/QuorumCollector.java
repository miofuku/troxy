package reptor.replct.common.quorums;

import reptor.distrbt.com.VerificationException;


public class QuorumCollector<M, V> extends AbstractQuorumCollector<M, V>
{

    public QuorumCollector(int nvoters, int threshold, int maxprops)
    {
        super( nvoters, threshold, maxprops );
    }


    public boolean isAlreadyKnown(int voter, M msg) throws VerificationException
    {
        return isAlreadyKnownInternal( voter, msg );
    }


    public boolean addVote(int voter, M msg, V vote, boolean isprop)
    {
        return addVoteInternal( voter, msg, vote, isprop );
    }

}
