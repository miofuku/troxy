package reptor.measr;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.base.MoreObjects;


public class IntervalObserverList implements IntervalObserver
{
    private final IntervalObserver[] m_observers;


    public IntervalObserverList(IntervalObserver... observers)
    {
        m_observers = Objects.requireNonNull( observers, "observers" );
    }


    public IntervalObserverList(Collection<? extends IntervalObserver> observers)
    {
        this( observers.toArray( new IntervalObserver[observers.size()] ) );
    }


    public static IntervalObserver createIfNecessary(IntervalObserver o0, IntervalObserver o1)
    {
        return MoreObjects.firstNonNull(
                LongIntervalValueSink.createCompositeIfNecessary( IntervalObserverList::new, o0, o1 ),
                IntervalObserver.EMPTY );
    }


    public static <T> IntervalObserver fromMapped(Collection<T> c, Function<? super T, ? extends IntervalObserver> mapper)
    {
        IntervalObserver[] intobs = c.stream().map( mapper ).toArray( IntervalObserver[]::new );

        if( intobs.length==0 )
            return null;
        else if( intobs.length==1 )
            return intobs[ 0 ];
        else
            return new IntervalObserverList( intobs );
    }


    @Override
    public void intervalStarted()
    {
        for( IntervalObserver o : m_observers )
            o.intervalStarted();
    }


    @Override
    public void intervalEnded()
    {
        for( IntervalObserver o : m_observers )
            o.intervalEnded();
    }


    @Override
    public void intervalCancelled()
    {
        for( IntervalObserver o : m_observers )
            o.intervalCancelled();
    }

}
