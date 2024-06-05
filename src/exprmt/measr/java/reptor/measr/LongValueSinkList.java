package reptor.measr;

import java.util.Collection;
import java.util.Objects;
import java.util.function.LongConsumer;

import com.google.common.base.MoreObjects;


public class LongValueSinkList implements LongValueSink
{
    private final LongValueSink[] m_sinks;


    public LongValueSinkList(LongValueSink... sinks)
    {
        m_sinks = Objects.requireNonNull( sinks, "sinks" );
    }


    public LongValueSinkList(Collection<LongValueSink> sinks)
    {
        this( sinks.toArray( new LongValueSink[sinks.size()] ) );
    }


    public static LongValueSink createIfNecessary(LongValueSink s0, LongValueSink s1)
    {
        return MoreObjects.firstNonNull(
                LongIntervalValueSink.createCompositeIfNecessary( LongValueSinkList::new, s0, s1 ),
                LongValueSink.EMPTY );
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
}
