package reptor.replct.network;

import java.util.List;

import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.distrbt.com.Message;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.checkpoint.CheckpointNetworkMessage;
import reptor.replct.agree.view.ViewChangeNetworkMessage;


public interface ReplicaPeers extends AgreementPeers
{
    int getInternalCheckpointCoordinator(CheckpointNetworkMessage msg);
    int getInternalViewChangeHandler(ViewChangeNetworkMessage msg);

    List<? extends DomainEndpoint<PushMessageSink<Message>>> getReplicaNetworks();
}
