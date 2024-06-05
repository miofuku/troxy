package reptor.replct.invoke;

import reptor.chronos.orphics.Actor;
import reptor.replct.connect.CommunicationMessages.NewConnection;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;


public interface InvocationReplicaHandler extends Actor
{

    short   getClientNumber();

    void    initContact(byte contact);

    void    enqueueNewConnection(NewConnection newconn);
    void    enqueueRequest(Request request);
    void    enqueueRequestExecuted(RequestExecuted reqexecd);
    void    enqueueReply(Reply reply);

    Request pollPendingRequests();

}
