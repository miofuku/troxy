package reptor.chronos.link;

import java.util.List;
import java.util.Objects;

import reptor.chronos.com.PushMessageSink;


public class RoundRobinLink<M, L extends PushMessageSink<? super M>> implements PushMessageSink<M>
{

    protected final List<L> m_links;

    protected long m_round;

    public RoundRobinLink(List<L> links)
    {
        m_links = Objects.requireNonNull( links );
        m_round = hashCode();
    }

    public RoundRobinLink(List<L> links, long startround)
    {
        m_links = Objects.requireNonNull( links );
        m_round = startround;
    }

    @Override
    public void enqueueMessage(M msg)
    {
        selectLink().enqueueMessage( msg );
    }

    public int size()
    {
        return m_links.size();
    }

    protected L selectLink()
    {
        return m_links.get( (int) ( Math.abs( m_round++ % m_links.size() ) ) );
    }

}
