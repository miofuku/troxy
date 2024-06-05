package reptor.chronos.link;

import java.util.List;

import reptor.chronos.com.PushMessageSink;


public class RoundRobinMulticastLink<M> extends RoundRobinLink<M, MulticastLink<? super M>>
                                        implements MulticastLink<M>
{

    public RoundRobinMulticastLink(List<MulticastLink<? super M>> linkls)
    {
        super( linkls );
    }

    public RoundRobinMulticastLink(List<MulticastLink<? super M>> links, long startround)
    {
        super( links, startround );
    }

    @Override
    public void enqueueUnicast(int link, M msg)
    {
        selectLink().enqueueUnicast( link, msg );
    }

    @Override
    public PushMessageSink<? super M> getLink(int link)
    {
        return selectLink().getLink( link );
    }

}
