package reptor.replct.invoke;

import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.connect.HandshakingProcess;


public interface InvocationClient
{

    HandshakingProcess<?>       createHandshake(short clino);

    InvocationClient            connectionObserver(ConnectionObserver connobs);

    InvocationClientProvider    createInvocationProvider();

    Invocation          getInvocation();
    ConnectionObserver  getConnectionObserver();
    int[][]             getSummary();

}
