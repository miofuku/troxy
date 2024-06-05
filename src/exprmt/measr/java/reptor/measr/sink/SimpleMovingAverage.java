package reptor.measr.sink;

import java.util.Arrays;

import reptor.measr.LongValueSink;


public class SimpleMovingAverage implements LongValueSink
{
    private final long[] m_vals;
    private long         m_valsum = 0;
    private int          m_valcnt = 0;


    public SimpleMovingAverage(int window)
    {
        m_vals = new long[window];
    }


    public int getCount()
    {
        return Math.min( m_valcnt, m_vals.length );
    }


    public long getSum()
    {
        return m_valsum;
    }


    public long getMean()
    {
        return m_valcnt > 0 ? m_valsum / getCount() : 0;
    }


    public double getDoubleMean()
    {
        return m_valcnt > 0 ? m_valsum / (double) getCount() : 0.0;
    }


    @Override
    public void accept(long value)
    {
        m_valsum += value - m_vals[curIndex()];
        m_vals[curIndex()] = value;
        m_valcnt++;
    }


    @Override
    public void reset()
    {
        m_valcnt = 0;
        m_valsum = 0;
        Arrays.fill( m_vals, 0 );
    }


    private int curIndex()
    {
        return m_valcnt % m_vals.length;
    }
}
