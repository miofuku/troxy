package reptor.chronos.com;

import reptor.chronos.Asynchronous;
import reptor.chronos.Commutative;

//Contrary to an unbuffered message sink, a push message sink has to consume all passed messages.
@Commutative
@FunctionalInterface
public interface PushMessageSink<M> extends CommunicationSink
{
    @Asynchronous
    void enqueueMessage(M msg);
}
