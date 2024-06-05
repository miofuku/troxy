package reptor.chronos.link;

import java.util.List;
import java.util.function.Function;

import reptor.chronos.com.PushMessageSink;


public class SelectorLink<M> implements PushMessageSink<M>
{

    private final List<PushMessageSink<? super M>> m_links;
    private final Function<M, Integer>             m_selector;

    public SelectorLink(List<PushMessageSink<? super M>> links, Function<M, Integer> selector)
    {
        m_links    = links;
        m_selector = selector;
    }

    @Override
    public void enqueueMessage(M msg)
    {
        m_links.get( m_selector.apply( msg ) ).enqueueMessage( msg );
    }

}
