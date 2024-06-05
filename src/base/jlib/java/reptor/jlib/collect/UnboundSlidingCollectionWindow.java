package reptor.jlib.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;


public class UnboundSlidingCollectionWindow<V> implements SlidingCollectionWindow<V>
{

    private static class Entry<V>
    {
        private final long m_slotno;
        private final V    m_elem;

        public Entry(long slotno, V elem)
        {
            m_slotno = slotno;
            m_elem   = elem;
        }

        public long getSlotNumber()
        {
            return m_slotno;
        }

        public V getElement()
        {
            return m_elem;
        }
    }


    private final Object[] m_slots;

    private long m_wndstart = 0;
    private int  m_nelems   = 0;


    public UnboundSlidingCollectionWindow(int capacity)
    {
        m_slots = new Object[ capacity ];
        Arrays.setAll( m_slots, x -> new LinkedList<Entry<V>>() );
    }


    @Override
    public int size()
    {
        return Integer.MAX_VALUE;
    }


    @Override
    public long getWindowStart()
    {
        return m_wndstart;
    }


    @Override
    public long getWindowEnd()
    {
        return Long.MAX_VALUE;
    }


    @Override
    public int getNumberOfElements()
    {
        return m_nelems;
    }


    @Override
    public boolean hasElements(long slotno)
    {
        checkWindow( slotno );

        return Iterators.any( getSlot( slotno ).iterator(), x -> x.getSlotNumber()==slotno );
    }


    @Override
    public int getNumberOfElements(long slotno)
    {
        return Iterators.size( elements( slotno ) );
    }


    @Override
    public UnmodifiableIterator<V> elements(long slotno)
    {
        checkWindow( slotno );

        Iterator<Entry<V>> filit = Iterators.filter( getSlot( slotno ).iterator(), x -> x.getSlotNumber()==slotno );
        Iterator<V>        trait = Iterators.transform( filit, x -> x.getElement() );

        return Iterators.unmodifiableIterator( trait );
    }


    @Override
    public void add(long slotno, V elem)
    {
        checkWindow( slotno );

        getSlot( slotno ).add( new Entry<>( slotno, elem ) );
        m_nelems++;
    }


    @Override
    public boolean forwardWindow(Consumer<? super V> action)
    {
        Objects.requireNonNull( action );

        boolean modified;

        if( !hasElements( m_wndstart ) )
            modified = false;
        else
        {
            removeSlot( m_wndstart, action );

            modified = true;
        }

        m_wndstart++;

        return modified;
    }


    @Override
    public void skipSlots(long newwndstart)
    {
        if( newwndstart<m_wndstart )
            throw new IllegalArgumentException( "New start for the window (" + newwndstart +
                                                ") is lower then current start (" + m_wndstart + ")" );

        if( newwndstart>=getWindowEnd() )
            clear();
        else
        {
            for( long slotno=m_wndstart, end=newwndstart; slotno<end; slotno++ )
                removeSlot( slotno, null );
        }

        m_wndstart = newwndstart;
    }


    private void removeSlot(long slotno, Consumer<? super V> action)
    {
        for( Iterator<Entry<V>> it = getSlot( slotno ).iterator(); it.hasNext(); )
        {
            Entry<V> entry = it.next();

            if( entry.getSlotNumber()==slotno )
            {
                if( action!=null )
                    action.accept( entry.getElement() );

                it.remove();
                m_nelems--;
            }
        }
    }


    @Override
    public void clear()
    {
        for( int i=0; i<m_slots.length; i++ )
            getSlotByIndex( i ).clear();

        m_nelems = 0;
    }


    private Collection<Entry<V>> getSlot(long slotno)
    {
        return getSlotByIndex( getSlotIndex( slotno ) );
    }


    private int getSlotIndex(long slotno)
    {
        return (int) ( slotno % m_slots.length );
    }


    @SuppressWarnings("unchecked")
    private Collection<Entry<V>> getSlotByIndex(int index)
    {
        return (Collection<Entry<V>>) m_slots[ index ];
    }


    private void checkWindow(long slotno)
    {
        if( slotno<getWindowStart() )
            throw new IllegalArgumentException( "Slot " + slotno + " not within window starting from " +
                                                getWindowStart() );
    }

}
