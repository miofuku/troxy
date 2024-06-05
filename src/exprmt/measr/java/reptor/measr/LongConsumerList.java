package reptor.measr;

import java.util.Collection;
import java.util.Objects;
import java.util.function.LongConsumer;

import com.google.common.base.MoreObjects;


public class LongConsumerList implements LongConsumer
{
    private final LongConsumer[] m_items;


    public LongConsumerList(LongConsumer... items)
    {
        m_items = Objects.requireNonNull( items, "items" );
    }


    public LongConsumerList(Collection<LongConsumer> items)
    {
        this( items.toArray( new LongConsumer[items.size()] ) );
    }


    public static LongConsumer createIfNecessary(LongConsumer c0, LongConsumer c1)
    {
        return MoreObjects.firstNonNull(
                LongIntervalValueSink.createCompositeIfNecessary( LongConsumerList::new, c0, c1 ),
                LongValueSink.EMPTY );
    }


    @Override
    public void accept(long value)
    {
        for( LongConsumer i : m_items )
            i.accept( value );
    }
}
