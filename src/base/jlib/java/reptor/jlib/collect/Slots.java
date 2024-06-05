package reptor.jlib.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;


public class Slots<V>  implements Collection<V>
{

    private final Object[] m_slots;

    private int m_size = 0;


    public Slots(int capacity)
    {
        m_slots = new Object[ capacity ];
    }


    public Slots(int capacity, IntFunction<? extends V> generator)
    {
        this( capacity );

        for( int i=0; i<capacity; i++ )
            if( ( m_slots[ i ] = generator.apply( i ) )!=null )
                m_size++;
    }


    @Override
    public int size()
    {
        return m_size;
    }

    public int capacity()
    {
        return m_slots.length;
    }

    public int emptySlotsCount()
    {
        return m_slots.length-m_size;
    }

    @Override
    public boolean isEmpty()
    {
        return m_size==0;
    }


    public boolean containsKey(int key)
    {
        return m_slots[ key ]!=null;
    }

    @Override
    public boolean contains(Object value)
    {
        if( value==null )
            return m_size!=m_slots.length;
        else
            return getKey( value )!=-1;
    }

    @Override
    public boolean containsAll(Collection<?> c)
    {
        for( Object value : c )
            if( !contains( value ) )
                return false;
        return true;
    }


    public int getKey(Object value)
    {
        for( int i=0; i<m_slots.length; i++ )
            if( value.equals( m_slots[ i ] ) )
                return i;
        return -1;
    }

    @SuppressWarnings("unchecked")
    public V get(int key)
    {
        return (V) m_slots[ key ];
    }


    @Override
    public boolean add(V e)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends V> c)
    {
        throw new UnsupportedOperationException();
    }

    public V put(int key, V value)
    {
        if( value==null )
            return removeKey( key );
        else
        {
            V o = get( key );

            m_slots[ key ] = value;

            if( o==null )
                m_size++;

            return o;
        }
    }

    public void putAll(Map<? extends Integer, ? extends V> m)
    {
        for( Map.Entry<? extends Integer, ? extends V> e : m.entrySet() )
            put( e.getKey(), e.getValue() );
    }


    public V removeKey(int key)
    {
        V o = get( key );

        m_slots[ key ] = null;

        if( o!=null )
            m_size--;

        return o;
    }

    @Override
    public boolean remove(Object value)
    {
        int key = getKey( value );

        if( key==-1 )
            return false;
        else
        {
            removeKey( key );
            return true;
        }
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        boolean modified = false;

        for( Object value : c )
            modified = remove( value ) || modified;

        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        boolean modified = false;

        Iterator<V> it = iterator();
        while( it.hasNext() )
        {
            if( !c.contains( it.next() ) )
            {
                it.remove();
                modified = true;
            }
        }

        return modified;
    }


    @Override
    public void clear()
    {
        if( m_size==0 )
            return;

        for( int i=0; i<m_slots.length; i++ )
            m_slots[ i ] = null;

        m_size = 0;
    }


    private abstract class IteratorBase<E> implements Iterator<E>
    {
        private int m_left;
        private int m_curidx;

        public IteratorBase()
        {
            m_left   = m_size;
            m_curidx = -1;
        }

        @Override
        public boolean hasNext()
        {
            return m_left>0;
        }

        public int nextKey()
        {
            if( m_left==0 )
                throw new NoSuchElementException();

            while( m_slots[ ++m_curidx ]==null ) ;

            m_left--;

            return m_curidx;
        }

        @Override
        public void remove()
        {
            if( m_curidx==-1 || m_slots[ m_curidx ]==null )
                throw new IllegalStateException();

            removeKey( m_curidx );
        }
    }

    private class KeyIterator extends IteratorBase<Integer>
    {
        @Override
        public Integer next()
        {
            return nextKey();
        }
    }

    private class ValueIterator extends IteratorBase<V>
    {
        @Override
        public V next()
        {
            return get( nextKey() );
        }
    }

    public Iterator<Integer> keyIterator()
    {
        return new KeyIterator();
    }

    @Override
    public Iterator<V> iterator()
    {
        return new ValueIterator();
    }


    @Override
    public Object[] toArray()
    {
        return toArray( new Object[ m_size ] );
    }


    public V[] toArray(IntFunction<V[]> factory)
    {
        return toArray( factory.apply( m_size ) );
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] array)
    {
        if( array.length!=m_size )
        {
            if( array.length<m_size )
                array = (T[]) java.lang.reflect.Array.newInstance( array.getClass().getComponentType(), m_size );
            else
                array[ m_size ] = null;
        }

        int arridx = 0;

        for( int i=0; i<m_slots.length && arridx<m_size; i++ )
        {
            if( m_slots[ i ]!=null )
                array[ arridx++ ] = (T) m_slots[ i ];
        }

        return array;
    }

}
