package reptor.chronos.com;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;


public interface MessageQueue<M> extends PushMessageSink<M>, Queue<M>
{
    boolean isReady();

    void    retrieveMessages();

    @Override
    M       poll();

    @Override
    default boolean add(M msg)
    {
        enqueueMessage( msg );
        return true;
    }

    @Override
    default boolean addAll(Collection<? extends M> msgs)
    {
        for( M msg : msgs )
            enqueueMessage( msg );
        return true;
    }

    @Override
    default boolean offer(M msg)
    {
        enqueueMessage( msg );
        return true;
    }

    @Override
    default M remove()
    {
        M msg = poll();

        if( msg==null )
            throw new NoSuchElementException();

        return msg;
    }

    @Override
    default M element()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default M peek()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default int size()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default Iterator<M> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean removeAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean remove(Object o)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean retainAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean containsAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean contains(Object o)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    default <T> T[] toArray(T[] a)
    {
        throw new UnsupportedOperationException();
    }
}
