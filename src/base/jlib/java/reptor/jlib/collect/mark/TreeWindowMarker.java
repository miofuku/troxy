package reptor.jlib.collect.mark;

import java.util.Map;
import java.util.TreeMap;

import reptor.jlib.collect.FixedSlidingWindow;


public class TreeWindowMarker<V> implements SlidingWindowMarker<V>
{

    private final FixedSlidingWindow<V> m_slots;
    private final TreeMap<Long, V>      m_marks = new TreeMap<>();


    public TreeWindowMarker(FixedSlidingWindow<V> slots)
    {
        m_slots = slots;
    }


    @Override
    public void add(long slotno)
    {
        m_marks.put( slotno, m_slots.getSlot( slotno ) );
    }


    @Override
    public void removeTo(long firstslotno)
    {
        m_marks.keySet().removeIf( x -> x<firstslotno );
    }


    @Override
    public V poll()
    {
        Map.Entry<Long, V> entry = m_marks.pollFirstEntry();

        return entry!=null ? entry.getValue() : null;
    }


    @Override
    public boolean isEmpty()
    {
        return m_marks.isEmpty();
    }


    @Override
    public void clear()
    {
        m_marks.clear();
    }

}
