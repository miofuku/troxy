package reptor.replct.common.quorums;

import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;


public class SenderQuorumCollector<M extends NetworkMessage, V> extends AbstractQuorumCollector<M, V>
{

    public SenderQuorumCollector(int nvoters, int threshold, int maxprops)
    {
        super( nvoters, threshold, maxprops );
    }


    public boolean isAlreadyKnown(M msg) throws VerificationException
    {
        return isAlreadyKnownInternal( voter( msg ), msg );
    }


    public boolean addVote(M msg, V vote, boolean isprop)
    {
        return addVoteInternal( voter( msg ), msg, vote, isprop );
    }


    protected int voter(M msg)
    {
        return msg.getSender();
    }

}
