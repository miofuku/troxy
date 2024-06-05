package reptor.chronos.link;

import reptor.chronos.Commutative;
import reptor.chronos.com.PushMessageSink;


@Commutative
public interface MulticastLink<M> extends PushMessageSink<M>
{
    void                        enqueueUnicast(int link, M msg);

    PushMessageSink<? super M>  getLink(int link);

    int size();
}
