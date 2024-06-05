package reptor.chronos.link;

import java.util.List;

import reptor.chronos.com.PushMessageSink;


public class RoundRobinPushSinkLink<M> extends RoundRobinLink<M, PushMessageSink<? super M>>
{

    public RoundRobinPushSinkLink(List<PushMessageSink<? super M>> links)
    {
        super( links );
    }

    public RoundRobinPushSinkLink(List<PushMessageSink<? super M>> links, long startround)
    {
        super( links, startround );
    }

}
