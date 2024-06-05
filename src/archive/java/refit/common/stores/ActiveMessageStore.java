package refit.common.stores;

import reptor.chronos.Actor;
import reptor.chronos.PushMessageSink;

@Deprecated
public interface ActiveMessageStore<M> extends MessageStore<M>, Actor, PushMessageSink<M>
{

}
