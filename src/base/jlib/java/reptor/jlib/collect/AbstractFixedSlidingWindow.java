package reptor.jlib.collect;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;


public class AbstractFixedSlidingWindow<V> implements SlidingWindow
{

    public interface SlotIterator<V> extends Iterator<V>
    {
        V    current();
        long currentSlotNumber();
    }


    private final V[] m_slots;

    private long m_wndstart;
    private long m_wndend;


    @SuppressWarnings("unchecked")
    public AbstractFixedSlidingWindow(Class<?> clazz, int size, IntFunction<? extends V> generator, long wndstart)
    {
        m_slots = (V[]) Array.newInstance( clazz, size );
        Arrays.setAll( m_slots, generator );

        setWindowStart( wndstart );
    }


    public AbstractFixedSlidingWindow(V[] slots, long wndstart)
    {
        m_slots = Objects.requireNonNull( slots );

        setWindowStart( wndstart );
    }


    @Override
    public int size()
    {
        return m_slots.length;
    }


    @Override
    public long getWindowStart()
    {
        return m_wndstart;
    }


    @Override
    public long getWindowEnd()
    {
        return m_wndend;
    }


    protected void setWindowStart(long newwndstart)
    {
        m_wndstart = newwndstart;
        m_wndend   = newwndstart + m_slots.length;
    }


    protected V forwardWindowInternal()
    {
        setWindowStart( m_wndstart+1 );

        return getSlotInternal( m_wndstart-1 );
    }


    protected long forwardWindowInternal(long newwndstart)
    {
        if( newwndstart<getWindowStart() )
            throw new IllegalArgumentException( "New start for the window (" + newwndstart +
                                                ") is lower then current start (" + getWindowStart() + ")" );

        long firstnew = Math.max( getWindowEnd(), newwndstart );

        setWindowStart( newwndstart );

        return firstnew;
    }


    protected SlotIterator<V> slots(long startno, long endno)
    {
        return new SlotIterator<V>()
            {
                private long m_nextno = startno;

                @Override
                public boolean hasNext()
                {
                    return m_nextno<endno;
                }

                @Override
                public V next()
                {
                    if( !hasNext() )
                        throw new NoSuchElementException();

                    return getSlotInternal( m_nextno++ );
                }

                @Override
                public V current()
                {
                    return getSlotInternal( currentSlotNumber() );
                }

                @Override
                public long currentSlotNumber()
                {
                    if( m_nextno==startno )
                        throw new NoSuchElementException();

                    return m_nextno-1;
                }
            };
    }


    protected void forEachInternal(Consumer<V> action)
    {
        for( long current=m_wndstart; current<m_wndend; current++ )
            action.accept( getSlotInternal( current ) );
    }


    protected void forEachInternal(LongBiConsumer<V> action)
    {
        for( long current=m_wndstart; current<m_wndend; current++ )
            action.accept( current, getSlotInternal( current ) );
    }


    protected V getSlotInternal(long slotno)
    {
        return getSlotByIndex( getSlotIndex( slotno ) );
    }


    protected V tryGetSlotInternal(long slotno)
    {
        return isWithinWindow( slotno ) ? getSlotByIndex( getSlotIndex( slotno ) ) : null;
    }


    protected int getSlotIndex(long slotno)
    {
        return (int) ( slotno % m_slots.length );
    }


    protected V getSlotByIndex(int index)
    {
        return m_slots[ index ];
    }


    protected void checkWindow(long slotno)
    {
        if( !isWithinWindow( slotno ) )
            throw new IllegalArgumentException( "Slot " + slotno + " not within window " +
                                                getWindowStart() + "-" + getWindowEnd() );
    }

}
