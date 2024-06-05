package reptor.chronos.orphics;

import reptor.chronos.Asynchronous;
import reptor.chronos.Orphic;
import reptor.chronos.context.ChronosDomainContext;


public interface MessageQueueHandler<Q> extends Orphic
{
    ChronosDomainContext getDomainContext();

    @Asynchronous
    void messagesReady(Q queue);
}
