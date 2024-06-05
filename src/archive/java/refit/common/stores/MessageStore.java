package refit.common.stores;

import reptor.chronos.Orphic;
import reptor.chronos.PushMessageSink;


@Deprecated
public interface MessageStore<M> extends Orphic, PushMessageSink<M>, Iterable<M>
{
    int size();
}
