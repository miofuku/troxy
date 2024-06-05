package reptor.measr.sink;

import reptor.measr.LongValueSink;


// -- Deriving SimpleSummaryCollector from SimpleStatisticsSummary is problematic because it
// -- could be thought of it as a value type (compare notes on the Meter interface and why meters
// -- should not be quantity values). However, here it is not as clear as in the case of quantity
// -- values since here we talk about summaries of measurement results where the measurement could
// -- still be ongoing whereas a single measured quantity value never changes of time.
// --
// -- We do not use LongSummaryStatistic because its instances cannot be reset.
public class LongStatsSink implements LongStatisticalSummary, LongValueSink
{
    private long m_cnt = 0;
    private long m_sum = 0;
    private long m_min = Long.MAX_VALUE;
    private long m_max = Long.MIN_VALUE;


    public LongStatsSink add(LongStatsSink s)
    {
        return add( s.m_cnt, s.m_sum, s.m_min, s.m_max );
    }


    public LongStatsSink add(long cnt, long sum, long min, long max)
    {
        if( m_cnt == 0 )
        {
            m_min = min;
            m_max = max;
        }
        else if( cnt != 0 )
        {
            m_min = Math.min( m_min, min );
            m_max = Math.max( m_max, max );
        }

        m_cnt += cnt;
        m_sum += sum;

        return this;
    }


    @Override
    public void accept(long value)
    {
        m_cnt++;
        m_sum += value;
        m_min = Math.min( m_min, value );
        m_max = Math.max( m_max, value );
    }


    @Override
    public void reset()
    {
        m_cnt = m_sum = 0;
        m_min = Long.MAX_VALUE;
        m_max = Long.MIN_VALUE;
    }


    @Override
    public long getCount()
    {
        return m_cnt;
    }


    @Override
    public long getSum()
    {
        return m_sum;
    }


    @Override
    public long getMin()
    {
        return m_min;
    }


    @Override
    public long getMax()
    {
        return m_max;
    }


    @Override
    public long getMean()
    {
        return m_cnt > 0 ? (m_sum + m_cnt - 1) / m_cnt : 0;
    }
}
