package reptor.jlib.collect;

public interface SlidingWindow
{
    int    size();
    long    getWindowStart();
    long    getWindowEnd();

    default boolean isWithinWindow(long slotno)
    {
        return slotno>=getWindowStart() && slotno<getWindowEnd();
    }

    default boolean isBelowWindow(long slotno)
    {
        return slotno<getWindowStart();
    }

    default boolean isAboveWindow(long slotno)
    {
        return slotno>=getWindowEnd();
    }
}
