package reptor.measr.compose;

import java.util.function.Supplier;

import reptor.measr.IntervalObserver;
import reptor.measr.IntervalObserverList;
import reptor.measr.LongIntervalValueSink;
import reptor.measr.LongValueSink;
import reptor.measr.LongValueSinkList;
import reptor.measr.Meter;
import reptor.measr.filter.FilteredIntervalMeterBuilder;
import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.meter.IntervalResult;
import reptor.measr.meter.LongHistorySummaryMeter;


//
// <measured value>  -->|       |--> IntervalSink (log)
//                      |------>|    ~ perceives all intervals
//                      |       |
// IntervalGenerator -->|       |--> Filter ------------------> IntervalMeter (rec)
// ~ generates clock signal          ~ suppresses warm-up  |    ~ summary of recording
//                                                         |
//                                                         |--> IntervalMeter (int)
//

public class QuantityMeasurement<S extends LongValueSink, I extends LongValueSink>
        implements LongIntervalValueSink, IntervalHistorySummary<S, I>, Meter
{
    private final LongValueSink                m_valsnk;
    private final IntervalObserver             m_intobs;
    private final IntervalHistorySummary<S, I> m_result;


    public QuantityMeasurement(int delints, int recints, LongIntervalValueSink intlog,
            Supplier<? extends S> sumfac, Supplier<? extends I> hisfac)
    {
        if( intlog == null && sumfac == null && hisfac == null )
            throw new IllegalArgumentException( "At least one sink or sink factory has to be passed." );
        if( intlog == null && recints <= 0 )
            throw new IllegalArgumentException( "Intervals have to be either logged or recorded." );

        LongHistorySummaryMeter<S, I> meter;
        IntervalObserver filobs;

        if( recints <= 0 || (sumfac == null && hisfac == null) )
        {
            meter = null;
            filobs = null;
            m_result = IntervalHistorySummary.create( null, null );
        }
        else
        {
            FilteredIntervalMeterBuilder filmetbld = new FilteredIntervalMeterBuilder();
            meter = filmetbld.addQuantity( sumfac, hisfac, recints );
            filobs = filmetbld.createCounting( delints, recints );
            m_result = meter;
        }

        m_valsnk = LongValueSinkList.createIfNecessary( intlog, meter );
        m_intobs = IntervalObserverList.createIfNecessary( intlog, filobs );
    }


    @Override
    public void accept(long value)
    {
        m_valsnk.accept( value );
    }


    @Override
    public void reset()
    {
        m_valsnk.reset();
    }


    @Override
    public void intervalStarted()
    {
        m_intobs.intervalStarted();
    }


    @Override
    public void intervalEnded()
    {
        m_intobs.intervalEnded();
    }


    @Override
    public void intervalCancelled()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public int getNumberOfAvailInts()
    {
        return m_result.getNumberOfAvailInts();
    }


    @Override
    public IntervalResult<I> getInterval(int index)
    {
        return m_result.getInterval( index );
    }


    @Override
    public IntervalResult<S> getSummary()
    {
        return m_result.getSummary();
    }
}
