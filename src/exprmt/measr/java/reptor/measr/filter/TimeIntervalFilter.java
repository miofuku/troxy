package reptor.measr.filter;

import reptor.measr.IntervalObserver;
import reptor.measr.meter.Stopwatch;


public class TimeIntervalFilter extends AutomaticIntervalFilter
{
    public static class Factory implements IntervalFilter.Factory<TimeIntervalFilter>
    {
        protected int m_deldur;
        protected int m_recdur;


        public Factory(int deldur, int recdur)
        {
            m_deldur = deldur;
            m_recdur = recdur;
        }


        @Override
        public TimeIntervalFilter create(IntervalObserver intobs, IntervalObserver recobs)
        {
            return new TimeIntervalFilter( m_deldur, m_recdur, intobs, recobs );
        }
    }


    private final Stopwatch m_stopwatch = new Stopwatch();


    public TimeIntervalFilter(long deldur, long recdur, IntervalObserver intobs, IntervalObserver recobs)
    {
        super( deldur, recdur, intobs, recobs );
    }


    @Override
    protected long startCounting()
    {
        long curspan = m_stopwatch.getElapsedTime();
        m_stopwatch.start();
        return curspan;
    }


    @Override
    protected long countInterval()
    {
        return m_stopwatch.getElapsedTime();
    }


    @Override
    protected void stopCounting()
    {
        m_stopwatch.stop();
    }


    @Override
    protected void cancelCounting()
    {
        m_stopwatch.cancel();
    }


    @Override
    protected boolean isIntRunning()
    {
        return m_stopwatch.isRecording();
    }
}
