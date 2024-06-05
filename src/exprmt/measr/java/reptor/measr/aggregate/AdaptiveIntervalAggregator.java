package reptor.measr.aggregate;

import reptor.measr.IntervalObserver;
import reptor.measr.meter.Stopwatch;
import reptor.measr.sink.SimpleMovingAverage;


public class AdaptiveIntervalAggregator extends CountingIntervalAggregator
{
    private final Stopwatch           m_stopwatch = new Stopwatch();
    private final long                m_durtarget;
    private final SimpleMovingAverage m_avgagg;


    public AdaptiveIntervalAggregator(IntervalObserver observer, long durtarget, int window)
    {
        super( observer );

        m_avgagg = new SimpleMovingAverage( window );
        m_durtarget = durtarget;
    }


    @Override
    protected void onAggregatedIntervalStarted()
    {
        if( m_stopwatch.isRecording() )
            adaptAggregateCount( m_stopwatch.getElapsedTime() );

        m_stopwatch.reset();
        m_stopwatch.start();

        super.onAggregatedIntervalStarted();
    }


    @Override
    protected void onAggregatedIntervalEnded()
    {
        assert m_stopwatch.isRecording();

        m_stopwatch.stop();
        adaptAggregateCount( m_stopwatch.getElapsedTime() );

        super.onAggregatedIntervalEnded();
    }


    private void adaptAggregateCount(long durcur)
    {
        int curagg = getAggregatorCount();

        m_avgagg.accept( (long) (m_durtarget / (double) durcur * curagg) );

        int nac = (int) m_avgagg.getMean();
        nac = Math.max( nac, curagg >> 2 );
        nac = Math.min( nac, curagg << 2 );
        nac = Math.max( nac, 1 );

        setAggregatorCount( nac );
    }
}
