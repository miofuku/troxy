package refit.pbfto.order;

import reptor.distrbt.com.NetworkMessage;


public interface PBFTOrderShardContext
{
    void enqueueProposal(NetworkMessage msg);
}
