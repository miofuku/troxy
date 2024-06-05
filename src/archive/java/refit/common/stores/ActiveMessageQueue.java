package refit.common.stores;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import reptor.replct.MessageHandler;

@Deprecated
public class ActiveMessageQueue<M> implements ActiveMessageStore<M>
{

    private final MessageHandler<M> m_handler;
    private final Queue<M>          m_inqueue;

    public ActiveMessageQueue(MessageHandler<M> handler)
    {
        this( handler, new ArrayDeque<>() );
    }

    public ActiveMessageQueue(MessageHandler<M> handler, Queue<M> inqueue)
    {
        m_handler = handler;
        m_inqueue = inqueue;
    }

    @Override
    public void enqueueMessage(M msg)
    {
        m_inqueue.add( msg );
    }

    @Override
    public boolean isReady()
    {
        return m_inqueue.size()>0;
    }

    @Override
    public boolean execute()
    {
        boolean changed = false;

        for( M msg : m_inqueue )
            changed = m_handler.handleMessage( msg ) || changed;

        m_inqueue.clear();

        return changed;
    }

    @Override
    public int size()
    {
        return m_inqueue.size();
    }

    @Override
    public Iterator<M> iterator()
    {
        return m_inqueue.iterator();
    }

}
