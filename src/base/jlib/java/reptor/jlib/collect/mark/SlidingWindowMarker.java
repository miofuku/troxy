package reptor.jlib.collect.mark;


public interface SlidingWindowMarker<V>
{

    void    add(long slotno);
    void    removeTo(long firstslotno);

    boolean isEmpty();
    V       poll();

    void    clear();

}
