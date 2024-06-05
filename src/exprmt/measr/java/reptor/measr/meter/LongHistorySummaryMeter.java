package reptor.measr.meter;

import reptor.measr.LongValueSink;
import reptor.measr.LongValueSinkList;
import reptor.measr.Meter;


public class LongHistorySummaryMeter<S extends LongValueSink, I extends LongValueSink>
        implements LongValueSink, IntervalHistorySummary<S, I>, Meter
{
    private final LongValueSink                m_valsnk;
    private final IntervalHistorySummary<S, I> m_result;


    public LongHistorySummaryMeter(LongIntervalMeter<S> summeter, LongHistoryMeter<I> hismeter)
    {
        m_valsnk = LongValueSinkList.createIfNecessary( summeter, hismeter );
        m_result = IntervalHistorySummary.create( summeter, hismeter );
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
