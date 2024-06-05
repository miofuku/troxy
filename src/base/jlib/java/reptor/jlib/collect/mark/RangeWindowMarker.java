package reptor.jlib.collect.mark;

import java.util.function.Predicate;

import reptor.jlib.collect.FixedSlidingWindow;


public class RangeWindowMarker<V> implements SlidingWindowMarker<V>
{

    private final FixedSlidingWindow<V> m_slots;
    private final Predicate<V>          m_ismarked;

    private long m_readyfrom;
    private long m_readyto;


    public RangeWindowMarker(FixedSlidingWindow<V> slots, Predicate<V> ismarked)
    {
        m_slots    = slots;
        m_ismarked = ismarked;

        clear();
    }


    private boolean marked(V t)
    {
        return m_ismarked.test( t );
    }


    @Override
    public void add(long slotno)
    {
        m_readyfrom = Math.min( slotno, m_readyfrom );
        m_readyto   = Math.max( slotno+1, m_readyto );
    }


    @Override
    public void removeTo(long firstslotno)
    {
        if( !isEmpty() )
        {
            if( firstslotno>=m_readyto )
                clear();
            else
                m_readyfrom = Math.max( firstslotno, m_readyfrom );
        }
    }


    @Override
    public V poll()
    {
        if( isEmpty() )
            return null;
        else
        {
            V ready = null;

            for( ; m_readyfrom<m_readyto; m_readyfrom++ )
            {
                V t = m_slots.getSlot( m_readyfrom );

                if( marked( t ) )
                {
                    ready = t;
                    break;
                }
            }

            if( isEmpty() )
                clear();

            return ready;
        }
    }


    @Override
    public boolean isEmpty()
    {
        return m_readyfrom>=m_readyto;
    }


    @Override
    public void clear()
    {
        m_readyfrom = Long.MAX_VALUE;
        m_readyto   = 0;
    }

}
