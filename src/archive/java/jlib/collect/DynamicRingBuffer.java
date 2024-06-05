package jlib.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import com.google.common.collect.Iterators;

// Congrats! You reinvented ArrayDeque ... which you already used at some points.
@Deprecated
public class DynamicRingBuffer<E> implements Queue<E>
{

    private static final int DEFAULT_CAPACITY = 8;

    private Object[] m_slots;
    private int      m_size = 0;
    private int      m_head = 0;
    private int      m_tail = 0;


    public DynamicRingBuffer()
    {
        this( DEFAULT_CAPACITY );
    }


    public DynamicRingBuffer(int capacity)
    {
        m_slots = new Object[ calcCapacity( 1, capacity ) ];
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


    @Override
    public boolean isEmpty()
    {
        return m_size==0;
    }


    @Override
    public boolean contains(Object o)
    {
        return o==null ? false : Iterators.contains( iterator(), o );
    }


    @Override
    public boolean containsAll(Collection<?> c)
    {
        for( Object value : c )
            if( !contains( value ) )
                return false;
        return true;
    }



    @Override
    public boolean add(E e)
    {
        ensureCapacity( ++m_size );

        m_slots[ m_head ] = e;
        m_head = incPos( m_head );

        return true;
    }


    @Override
    public boolean addAll(Collection<? extends E> c)
    {
        ensureCapacity( m_size+c.size() );

        for( E value : c )
            add( value );

        return true;
    }


    @Override
    public boolean offer(E e)
    {
        return add( e );
    }


    @Override
    public E element()
    {
        if( m_size==0 )
            throw new NoSuchElementException();

        return peek();
    }


    @Override
    public E peek()
    {
        return getElement( m_tail );
    }

    @Override
    public E remove()
    {
        if( m_size==0 )
            throw new NoSuchElementException();

        return poll();
    }


    @Override
    public E poll()
    {
        if( m_size==0 )
            return null;
        else
        {
            E elem = getElement( m_tail );

            m_slots[ m_tail ] = null;
            m_tail = incPos( m_tail );
            m_size--;

            return elem;
        }
    }


    @Override
    public boolean remove(Object o)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean removeAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean retainAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void clear()
    {
        Arrays.fill( m_slots, null );
        m_head = 0;
        m_tail = 0;
        m_size = 0;
    }


    @Override
    public Iterator<E> iterator()
    {
        return new Iterator<E>()
            {
                private int m_remain = m_size;
                private int m_curpos = m_tail;

                @Override
                public boolean hasNext()
                {
                    return m_remain>0;
                }

                @Override
                public E next()
                {
                    if( !hasNext() )
                        throw new NoSuchElementException();

                    E elem = getElement( m_curpos );

                    m_curpos = incPos( m_curpos );
                    m_remain--;

                    return elem;
                }
            };
    }


    @Override
    public Object[] toArray()
    {
        return toArray( new Object[ 0 ] );
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a)
    {
        T[] array  = (T[]) java.lang.reflect.Array.newInstance( a.getClass().getComponentType(), m_size );
        int arridx = 0;

        for( int i=0; i<m_slots.length && arridx<m_size; i++ )
        {
            if( m_slots[ i ]!=null )
                array[ arridx++ ] = (T) m_slots[ i ];
        }

        return array;
    }


    private void ensureCapacity(int mincap)
    {
        int curcap = m_slots.length;

        if( mincap<=curcap )
            return;

        int      newcap   = calcCapacity( curcap, mincap );
        Object[] newslots = new Object[ newcap ];

        if( m_size>0 )
        {
            if( m_head>m_tail )
                System.arraycopy( m_slots, m_tail, newslots, m_tail, m_head-m_tail );
            else
            {
                System.arraycopy( m_slots, 0, newslots, 0, m_head );

                int newtail = newcap - curcap + m_tail;
                System.arraycopy( m_slots, m_tail, newslots, newtail, curcap-m_tail );
                m_tail = newtail;
            }
        }

        m_slots = newslots;
    }


    @SuppressWarnings("unchecked")
    private E getElement(int idx)
    {
        return (E) m_slots[ idx ];
    }


    private int incPos(int pos)
    {
        return ( pos+1 ) & ( m_slots.length-1 );
    }


    private int calcCapacity(int curcap, int mincap)
    {
        int newcap = curcap;

        while( newcap<mincap )
            newcap <<= 1;

        return newcap;
    }

}
