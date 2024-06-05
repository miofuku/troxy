package reptor.measr;

import java.util.Collection;
import java.util.Objects;
import java.util.function.LongConsumer;

import com.google.common.base.MoreObjects;


public class LongIntervalValueSinkList implements LongIntervalValueSink
{
    private final LongIntervalValueSink[] m_sinks;


    public LongIntervalValueSinkList(LongIntervalValueSink... sinks)
    {
        m_sinks = Objects.requireNonNull( sinks, "sinks" );
    }


    public LongIntervalValueSinkList(Collection<LongIntervalValueSink> sinks)
    {
        this( sinks.toArray( new LongIntervalValueSink[sinks.size()] ) );
    }


    public static LongIntervalValueSink createIfNecessary(LongIntervalValueSink s0, LongIntervalValueSink s1)
    {
        return MoreObjects.firstNonNull(
                LongIntervalValueSink.createCompositeIfNecessary( LongIntervalValueSinkList::new, s0, s1 ),
                LongIntervalValueSink.EMPTY );
    }


    @Override
    public void accept(long value)
    {
        for( LongConsumer s : m_sinks )
            s.accept( value );
    }


    @Override
    public void reset()
    {
        for( LongValueSink s : m_sinks )
            s.reset();
    }


    @Override
    public void intervalStarted()
    {
        for( LongIntervalValueSink s : m_sinks )
            s.intervalStarted();
    }


    @Override
    public void intervalEnded()
    {
        for( LongIntervalValueSink s : m_sinks )
            s.intervalEnded();
    }


    @Override
    public void intervalCancelled()
    {
        for( LongIntervalValueSink s : m_sinks )
            s.intervalCancelled();
    }
}
