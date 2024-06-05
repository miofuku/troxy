package reptor.chronos.com;

import reptor.chronos.Asynchronous;

public interface Portal<M> extends MessageQueue<M>, DomainEndpoint<PushMessageSink<M>>
{
    @Asynchronous
    void    messagesReady();
}
