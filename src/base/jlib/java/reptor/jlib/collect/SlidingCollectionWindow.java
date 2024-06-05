package reptor.jlib.collect;

import java.util.function.Consumer;

import com.google.common.collect.UnmodifiableIterator;


public interface SlidingCollectionWindow<V> extends SlidingWindow
{

    int         getNumberOfElements();
    boolean     hasElements(long slotno);
    int         getNumberOfElements(long slotno);

    UnmodifiableIterator<V> elements(long slotno);

    void    add(long slotno, V elem);
    boolean forwardWindow(Consumer<? super V> action);
    void    skipSlots(long newwndstart);
    void    clear();

}
