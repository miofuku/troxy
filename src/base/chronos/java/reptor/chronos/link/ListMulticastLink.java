package reptor.chronos.link;

import java.util.List;
import java.util.Objects;

import reptor.chronos.Commutative;
import reptor.chronos.com.PushMessageSink;


@Commutative
public class ListMulticastLink<M> implements MulticastLink<M>
{

    private final List<PushMessageSink<? super M>> m_links;

    public ListMulticastLink(List<PushMessageSink<? super M>> links)
    {
        m_links = Objects.requireNonNull( links );
    }

    @Override
    public void enqueueMessage(M msg)
    {
        for( PushMessageSink<? super M> link : m_links )
            link.enqueueMessage( msg );
    }

    @Override
    public void enqueueUnicast(int link, M msg)
    {
        m_links.get( link ).enqueueMessage( msg );
    }

    @Override
    public PushMessageSink<? super M> getLink(int link)
    {
        return m_links.get( link );
    }

    @Override
    public int size()
    {
        return m_links.size();
    }

}
