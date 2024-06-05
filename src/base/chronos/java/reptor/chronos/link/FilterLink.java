package reptor.chronos.link;

import java.util.Collection;
import java.util.function.BiFunction;

import reptor.chronos.com.PushMessageSink;


public class FilterLink<M> implements PushMessageSink<M>
{

    private final Collection<PushMessageSink<? super M>> m_sublinks;
    private final BiFunction<M, Integer, Boolean>        m_filter;

    public FilterLink(Collection<PushMessageSink<? super M>> links, BiFunction<M, Integer, Boolean> filter)
    {
        m_sublinks = links;
        m_filter   = filter;
    }

    @Override
    public void enqueueMessage(M msg)
    {
        int i = 0;
        for( PushMessageSink<? super M> link : m_sublinks )
            if( m_filter.apply( msg, i++ ) )
                link.enqueueMessage( msg );
    }

    public Collection<PushMessageSink<? super M>> getLinks()
    {
        return m_sublinks;
    }

}
