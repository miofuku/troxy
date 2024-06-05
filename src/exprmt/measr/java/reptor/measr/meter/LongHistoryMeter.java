package reptor.measr.meter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import reptor.measr.LongValueSink;


public class LongHistoryMeter<S extends LongValueSink>
        extends LongIntervalMeter<S> implements IntervalHistory<IntervalResult<S>>
{
    // TODO: Introduce ring buffer collection or use something like
    // org.apache.commons.collections.buffer.CircularFifoBuffer
    // or com.google.common.collect.EvictingQueue
    private final ArrayList<IntSum<S>> m_entries;


    @SafeVarargs
    public LongHistoryMeter(Supplier<? extends S> sinkfac, int nentries, Listener<? super S>... listeners)
    {
        this( sinkfac, nentries, Arrays.asList( listeners ) );
    }


    public LongHistoryMeter(Supplier<? extends S> sinkfac, int nentries, Collection<Listener<? super S>> listeners)
    {
        super( sinkfac.get(), listeners );

        m_entries = new ArrayList<>( nentries );
        for( int i = 0; i < nentries; i++ )
            m_entries.add( new IntSum<>( sinkfac.get() ) );
    }


    @Override
    protected void onIntervalEnded(int intno, long dur, S sink)
    {
        super.onIntervalEnded( intno, dur, sink );

        setCurrentInt( m_entries.set( getEntryIdx( intno ), getCurrentInt() ) );
    }


    @Override
    public void reset()
    {
        for( int i = 0; i < getNumberOfAvailInts(); i++ )
            m_entries.get( i ).reset();

        super.reset();
    }


    @Override
    public int getNumberOfAvailInts()
    {
        return Math.min( m_entries.size(), getIntervalCount() );
    }


    @Override
    public IntervalResult<S> getInterval(int index)
    {
        int avail = getNumberOfAvailInts();

        if( index < 0 || index >= avail )
            throw new IllegalArgumentException( "Illegal index." );

        return getEntry( getIntervalCount() - avail + index );
    }


    @Override
    public S getValueSummary()
    {
        return isIntervalRunning() ? super.getValueSummary() : getEntry( getIntervalCount() ).getValueSummary();
    }


    @Override
    public long getElapsedTime()
    {
        return isIntervalRunning() ? super.getElapsedTime() : getEntry( getIntervalCount() ).getElapsedTime();
    }


    protected int getEntryIdx(int intno)
    {
        return intno % m_entries.size();
    }


    protected IntervalResult<S> getEntry(int intno)
    {
        assert intno >= getIntervalCount() - getNumberOfAvailInts() && intno < getIntervalCount();

        return m_entries.get( getEntryIdx( intno ) );
    }
}
