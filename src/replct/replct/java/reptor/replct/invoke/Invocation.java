package reptor.replct.invoke;

import reptor.replct.NetworkProtocolComponent;
import reptor.replct.connect.Handshaking;


public interface Invocation extends NetworkProtocolComponent
{
    ClientToWorkerAssignment    getClientToWorkerAssignment();
    int                         getInvocationWindowSize();

    Handshaking<?>              getReplicaHandshake();
    Handshaking<?>              getClientHandshake();
}
