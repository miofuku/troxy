package reptor.jlib.collect;

import java.util.function.Consumer;
import java.util.function.IntFunction;


public class FixedSlidingWindow<V> extends AbstractFixedSlidingWindow<V>
{

    public FixedSlidingWindow(Class<V> clazz, int size, IntFunction<? extends V> generator, long wndstart)
    {
        super( clazz, size, generator, wndstart );
    }


    public FixedSlidingWindow(V[] slots, long wndstart)
    {
        super( slots, wndstart );
    }


    public V getSlot(long slotno)
    {
        checkWindow( slotno );

        return getSlotInternal( slotno );
    }


    public V tryGetSlot(long slotno)
    {
        return tryGetSlotInternal( slotno );
    }


    public V getSlotUnchecked(long slotno)
    {
        return getSlotInternal( slotno );
    }


    public V forwardWindow()
    {
        return forwardWindowInternal();
    }


    public long forwardWindow(long newwndstart)
    {
        return forwardWindowInternal( newwndstart );
    }


    public void forEach(Consumer<V> action)
    {
        forEachInternal( action );
    }


    public void forEach(LongBiConsumer<V> action)
    {
        forEachInternal( action );
    }

}
