package reptor.measr.sink;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import reptor.measr.DoubleValueSink;
import reptor.measr.LongValueSink;

import java.util.SortedMap;
import java.util.TreeMap;


public class ShiftFrequencySink implements LongValueSink, Cloneable
{
    public static class Bin implements Cloneable
    {
        private final long m_valfrom;
        private final long m_valto;  // -- Could be 'extent' or something similar and an int value.
        private int        m_valcnt;


        public Bin(long valfrom, long valto, int valcnt)
        {
            assert valfrom < valto;

            m_valfrom = valfrom;
            m_valto = valto;
            m_valcnt = valcnt;
        }


        public long getFrom()
        {
            return m_valfrom;
        }


        public long getTo()
        {
            return m_valto;
        }


        public int getCount()
        {
            return m_valcnt;
        }


        public void incCount()
        {
            m_valcnt++;
        }


        public void addCount(int value)
        {
            m_valcnt += value;
        }


        public void setCount(int valcnt)
        {
            m_valcnt = valcnt;
        }


        @Override
        public Bin clone()
        {
            return new Bin( m_valfrom, m_valto, m_valcnt );
        }
    }


    private final int            m_shift;
    private final long           m_mask;
    private final Map<Long, Bin> m_bins = new HashMap<>();


    public ShiftFrequencySink(int shift)
    {
        if( shift > Long.SIZE - 2 )
            throw new IllegalArgumentException( "shift must not be greater than " + (Long.SIZE - 2) );

        m_shift = shift;
        m_mask = ~((1 << shift) - 1);
    }


    @Override
    public ShiftFrequencySink clone()
    {
        ShiftFrequencySink clone = new ShiftFrequencySink( m_shift );

        for( Bin b : m_bins.values() )
            clone.m_bins.put( b.getFrom(), b.clone() );

        return clone;
    }


    @Override
    public void reset()
    {
        // -- bins.clear() would preserve the capacity of the hash map, that is the size of the underlying table.
        // -- The entries are, however, deleted and have to be recreated once new values are added.
        for( Bin b : m_bins.values() )
            b.setCount( 0 );
    }


    @Override
    public void accept(long value)
    {
        long binidx = getBinIndex( value );
        Bin bin = m_bins.get( binidx );

        if( bin != null )
            bin.incCount();
        else
            addBin( binidx, 1 );
    }


    public void merge(ShiftFrequencySink dist)
    {
        checkCompatibility( dist );

        for( Entry<Long, Bin> e : dist.m_bins.entrySet() )
        {
            Bin b = m_bins.get( e.getKey() );

            if( b != null )
                b.addCount( e.getValue().getCount() );
            else
                m_bins.put( e.getKey(), e.getValue().clone() );
        }
    }


    public SortedMap<Long, Bin> createDistribution()
    {
        SortedMap<Long, Bin> condmap = new TreeMap<>();

        for( Bin b : m_bins.values() )
            condmap.put( b.getFrom(), b );

        return condmap;
    }


    public SummaryStatsSink generateSummary()
    {
        SummaryStatsSink stats = new SummaryStatsSink();

        fillSink( stats );

        return stats;
    }


    public DescriptiveStatsSink generateDescription()
    {
        DescriptiveStatsSink stats = new DescriptiveStatsSink();

        fillSink( stats );

        return stats;
    }


    private long getBinIndex(long value)
    {
        return value & m_mask;
    }


    private void addBin(long binidx, int cnt)
    {
        // Integer cnt = log.get( adjval );
        // log.put( adjval, cnt!=null ? cnt+1 : 1 );
        // -- Using boxed primitives creates new objects when the value is greater than 512 or something.
        // -- Presumably better: Using an own type that encapsulates longs.
        // -- Another option: An own implementation of a hash table that is based on primitive long arrays.
        // -- What about collisions and buckets in that case? Maybe a bit over-engineered...
        // -- -> Maybe it is, but: In the current version, a simple lookup leads to the creation of
        // -- a new object due to autoboxing (for values greater than 512 or something...)
        // -- TODO: The goal would be to construct an implementation that does not create new objects
        // -- during measurements, that is, creating new objects during a warm-up phase and for a final
        // -- summary would be acceptable.
        // -- -> Creating new bins is not a problem but creating short living objects for adding values is.
        // -- Therefore, a primitive long array would do it since adjval is only used for the lookup.
        m_bins.put( binidx, new Bin( binidx, binidx + (1 << m_shift), cnt ) );
    }


    private void checkCompatibility(ShiftFrequencySink other)
    {
        if( other.m_shift != m_shift )
            throw new IllegalArgumentException( "Distributions do not have the same resolution." );
    }


    private void fillSink(DoubleValueSink sink)
    {
        for( Bin b : m_bins.values() )
        {
            double val = b.getFrom() / 2.0 + (b.getTo() - 1) / 2.0; // -- The getTo() is exclusive -> -1
            for( int i = 0; i < b.getCount(); i++ )
                sink.accept( val );
        }
    }
}
