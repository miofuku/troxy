package reptor.replct.invoke;

import reptor.chronos.Asynchronous;
import reptor.chronos.orphics.Actor;
import reptor.replct.service.ServiceCommand;


public interface InvocationClientHandler extends Actor
{

    boolean        isConnected();

    @Asynchronous
    void           startInvocation(ServiceCommand command);
    ServiceCommand pollResult();

    int            getNumberOfOngoingInvocations();
    int            getMaximumNumberOfInvocations();

    void           getConflict();

}
