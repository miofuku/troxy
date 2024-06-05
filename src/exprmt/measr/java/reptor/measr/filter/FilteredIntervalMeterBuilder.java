package reptor.measr.filter;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import reptor.measr.IntervalObserver;
import reptor.measr.IntervalObserverList;
import reptor.measr.LongValueSink;
import reptor.measr.meter.LongHistoryMeter;
import reptor.measr.meter.LongHistorySummaryMeter;
import reptor.measr.meter.LongIntervalMeter;


public class FilteredIntervalMeterBuilder
{
    protected List<IntervalObserver> m_intobs = new LinkedList<>();
    protected List<IntervalObserver> m_recobs = new LinkedList<>();


    public FilteredIntervalMeterBuilder addIntervalObserver(IntervalObserver intobs)
    {
        m_intobs.add( intobs );
        return this;
    }


    public FilteredIntervalMeterBuilder addRecordObserver(IntervalObserver recobs)
    {
        m_recobs.add( recobs );
        return this;
    }


    public <S extends LongValueSink, I extends LongValueSink> LongHistorySummaryMeter<S, I>
            addQuantity(Supplier<? extends S> sumfac, Supplier<? extends I> hisfac, int recints)
    {
        LongIntervalMeter<S> summet = null;

        if( sumfac != null )
        {
            summet = new LongIntervalMeter<>( sumfac.get() );
            m_recobs.add( summet );
        }

        LongHistoryMeter<I> hismet = null;

        if( hisfac != null )
        {
            hismet = new LongHistoryMeter<>( hisfac, recints );
            m_intobs.add( hismet );
        }

        return new LongHistorySummaryMeter<>( summet, hismet );
    }


    public IntervalRecorder createRecorder(boolean start)
    {
        return new IntervalRecorder( start, createIntervalObserver(), createRecordObserver() );
    }


    public CountingIntervalFilter createCounting(int delints, int recints)
    {
        return new CountingIntervalFilter( delints, recints, createIntervalObserver(), createRecordObserver() );
    }


    public TimeIntervalFilter createTime(long deldur, long recdur)
    {
        return new TimeIntervalFilter( deldur, recdur, createIntervalObserver(), createRecordObserver() );
    }


    protected IntervalObserver combineObserver(List<IntervalObserver> obslst)
    {
        if( obslst.isEmpty() )
            return null;
        else if( obslst.size() == 1 )
            return obslst.get( 0 );
        else
            return new IntervalObserverList( obslst );
    }


    protected IntervalObserver createIntervalObserver()
    {
        return combineObserver( m_intobs );
    }


    protected IntervalObserver createRecordObserver()
    {
        return combineObserver( m_recobs );
    }
}
