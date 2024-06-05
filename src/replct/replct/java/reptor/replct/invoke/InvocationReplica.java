package reptor.replct.invoke;

import reptor.chronos.link.MulticastLink;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.connect.HandshakingProcess;


public interface InvocationReplica
{
    HandshakingProcess<?>       getHandshake();

    InvocationReplica           connectionObserver(ConnectionObserver connobs);

    InvocationReplicaProvider   createInvocationProvider(short clintshard, MessageMapper mapper,
                                                         MulticastLink<? super NetworkMessage> repconn);

    Invocation          getInvocation();
    ConnectionObserver  getConnectionObserver();

}
