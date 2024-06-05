package refit.pbfto.order;

import distrbt.com.transmit.MessageTransmitter;
import reptor.chronos.DomainEndpoint;
import reptor.chronos.Orphic;
import reptor.chronos.PushMessageSink;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.order.OrderExtensions.OrderInstanceObserver;


public interface OrderInstanceContext extends Orphic
{
    OrderInstanceMessageStore<OrderNetworkMessage> getMessageStore();

    OrderInstanceObserver getOrderInstanceObserver();

    void instanceReady();


    void instanceComplete(Snapshot result);


    OrderMessages.CommandContainer fetchProposal();


    int generateMessageSalt();


    DomainEndpoint<PushMessageSink<Message>> getWorker(int salt);

    MessageTransmitter getCertifyingReplicaTransmitter();

    void broadcastToReplicas(NetworkMessage msg);
}
