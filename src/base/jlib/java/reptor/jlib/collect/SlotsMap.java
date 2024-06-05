package reptor.jlib.collect;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class SlotsMap<V> extends AbstractMap<Integer, V> implements Map<Integer, V>
{

    private final Slots<V> m_slots;

    private Keys     m_keys     = null;
    private EntrySet m_entryset = null;


    public SlotsMap(int capacity)
    {
        m_slots = new Slots<>( capacity );
    }

    public SlotsMap(Slots<V> slots)
    {
        Objects.requireNonNull( slots );

        m_slots = slots;
    }

    @Override
    public int size()
    {
        return m_slots.size();
    }

    public int capacity()
    {
        return m_slots.capacity();
    }

    public int emptySlotsCount()
    {
        return m_slots.emptySlotsCount();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return m_slots.containsKey( (Integer) key );
    }

    @Override
    public boolean containsValue(Object value)
    {
        return m_slots.contains( value );
    }

    @Override
    public V get(Object key)
    {
        return m_slots.get( (Integer) key );
    }

    @Override
    public V put(Integer key, V value)
    {
        return m_slots.put( key, value );
    }

    @Override
    public V remove(Object key)
    {
        return m_slots.removeKey( (Integer) key );
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends V> m)
    {
        m_slots.putAll( m );
    }

    @Override
    public void clear()
    {
        m_slots.clear();
    }

    @Override
    public Set<Integer> keySet()
    {
        return m_keys!=null ? m_keys : ( m_keys = new Keys() );
    }

    @Override
    public Collection<V> values()
    {
        return m_slots;
    }

    @Override
    public Set<Map.Entry<Integer, V>> entrySet()
    {
        return m_entryset!=null ? m_entryset : ( m_entryset = new EntrySet() );
    }


    private class Keys extends AbstractSet<Integer>
    {
        @Override
        public int size()
        {
            return m_slots.size();
        }

        @Override
        public void clear()
        {
            m_slots.clear();
        }

        @Override
        public boolean contains(Object o)
        {
            return m_slots.containsKey( (Integer) o );
        }

        @Override
        public boolean remove(Object o)
        {
            return m_slots.removeKey( (Integer) o )!=null;
        }

        @Override
        public Iterator<Integer> iterator()
        {
            return m_slots.keyIterator();
        }
    }

    private class EntrySet extends AbstractSet<Map.Entry<Integer, V>>
    {
        @Override
        public int size()
        {
            return m_slots.size();
        }

        @Override
        public void clear()
        {
            m_slots.clear();
        }

        @Override
        public boolean contains(Object o)
        {
            if( !(o instanceof Map.Entry) )
                return false;

            Map.Entry<?,?> e = (Map.Entry<?,?>) o;

            if( e.getValue()==null )
                return false;

            Object c = get( e.getKey() );

            return e.getValue().equals( c );
        }

        @Override
        public boolean remove(Object o)
        {
            if( !contains( o ) )
                return false;

            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            m_slots.removeKey( (Integer) e.getKey() );

            return true;
        }

        @Override
        public Iterator<Map.Entry<Integer, V>> iterator()
        {
            return new EntryIterator();
        }
    }

    private class EntryIterator implements Iterator<Map.Entry<Integer, V>>
    {
        private final Iterator<Integer> m_keyit = m_slots.keyIterator();

        @Override
        public boolean hasNext()
        {
            return m_keyit.hasNext();
        }

        @Override
        public Map.Entry<Integer, V> next()
        {
            Integer idx = m_keyit.next();

            return new AbstractMap.SimpleEntry<>( idx, get( idx ) );
        }

        @Override
        public void remove()
        {
            m_keyit.remove();
        }
    }

}
