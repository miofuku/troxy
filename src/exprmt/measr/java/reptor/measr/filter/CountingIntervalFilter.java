package reptor.measr.filter;

import reptor.measr.IntervalObserver;


public class CountingIntervalFilter extends AutomaticIntervalFilter
{
    public static class Factory implements IntervalFilter.Factory<CountingIntervalFilter>
    {
        protected int m_delints;
        protected int m_recints;


        public Factory(int delints, int recints)
        {
            m_delints = delints;
            m_recints = recints;
        }


        @Override
        public CountingIntervalFilter create(IntervalObserver intobs, IntervalObserver recobs)
        {
            return new CountingIntervalFilter( m_delints, m_recints, intobs, recobs );
        }
    }


    private int     m_intcnt       = 0;
    private boolean m_isintrunning = false;


    // -- recints == 0 -> filter all, recints < 0 -> do not filter after delints
    public CountingIntervalFilter(int delints, int recints, IntervalObserver intobs, IntervalObserver recobs)
    {
        super( delints, recints, intobs, recobs );
    }


    @Override
    protected long startCounting()
    {
        m_isintrunning = true;

        return m_intcnt;
    }


    @Override
    protected long countInterval()
    {
        return ++m_intcnt;
    }


    @Override
    protected void stopCounting()
    {
        m_isintrunning = false;
    }


    @Override
    protected void cancelCounting()
    {
        m_isintrunning = false;
    }


    @Override
    protected boolean isIntRunning()
    {
        return m_isintrunning;
    }
}
