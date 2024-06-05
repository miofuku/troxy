package reptor.jlib.collect.mark;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

import reptor.jlib.collect.Slots;


public class SearchSlotMarker<V>
{

    private final Slots<V>      m_slots;
    private final Predicate<V>  m_ismarked;

    private int                 m_nmarked;


    public SearchSlotMarker(Slots<V> slots, Predicate<V> ismarked, boolean allmarked)
    {
        m_slots    = Objects.requireNonNull( slots );
        m_ismarked = Objects.requireNonNull( ismarked );
        m_nmarked  = allmarked ? m_slots.capacity() : 0;
    }


    private boolean marked(V t)
    {
        return m_ismarked.test( t );
    }


    public void addSlot(int slotno)
    {
        m_nmarked++;
    }


    public V removeSlot(int slotno)
    {
        assert m_nmarked>0;

        m_nmarked--;

        return m_slots.get( slotno );
    }


    public int removeFirstSlot()
    {
        if( isEmpty() )
            throw new NoSuchElementException();

        for( int i=0; i<m_slots.capacity(); i++ )
        {
            if( marked( m_slots.get( i ) ) )
            {
                m_nmarked--;
                return i;
            }
        }

        throw new IllegalStateException();
    }


    public V poll()
    {
        if( isEmpty() )
            return null;

        return m_slots.get( removeFirstSlot() );
    }


    public boolean isEmpty()
    {
        return m_nmarked==0;
    }


    public void clear()
    {
        m_nmarked = 0;
    }

}
