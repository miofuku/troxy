package reptor.jlib.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;


// slot number -> slot -> elements
public class FixedSlidingCollectionWindow<V> extends AbstractFixedSlidingWindow<Collection<V>> implements SlidingCollectionWindow<V>
{

    private int m_nelems = 0;


    public FixedSlidingCollectionWindow(int size)
    {
        super( Collection.class, size, x -> new ArrayList<V>(), 0 );
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

        return !getSlotInternal( slotno ).isEmpty();
    }

    @Override
    public int getNumberOfElements(long slotno)
    {
        checkWindow( slotno );

        return getSlotInternal( slotno ).size();
    }


    @Override
    public UnmodifiableIterator<V> elements(long slotno)
    {
        checkWindow( slotno );

        return Iterators.unmodifiableIterator( getSlotInternal( slotno ).iterator() );
    }


    @Override
    public void add(long slotno, V elem)
    {
        checkWindow( slotno );

        getSlotInternal( slotno ).add( elem );
        m_nelems++;
    }


    @Override
    public boolean forwardWindow(Consumer<? super V> action)
    {
        Collection<V> slot = forwardWindowInternal();

        if( slot.isEmpty() )
            return false;
        else
        {
            slot.forEach( action );
            clearSlot( slot );

            return true;
        }
    }


    @Override
    public void skipSlots(long newwndstart)
    {
        long current = forwardWindowInternal( newwndstart );
        long wndend  = getWindowEnd();

        while( current<wndend )
            clearSlot( getSlotInternal( current++ ) );
    }


    private void clearSlot(Collection<V> slot)
    {
        m_nelems -= slot.size();
        slot.clear();
    }


    @Override
    public void clear()
    {
        for( int i=0; i<size(); i++ )
            getSlotByIndex( i ).clear();

        m_nelems = 0;
    }

}
