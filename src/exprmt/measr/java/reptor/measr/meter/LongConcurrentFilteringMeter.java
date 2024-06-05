package reptor.measr.meter;

import java.util.Objects;

import reptor.measr.LongIntervalValueSink;
import reptor.measr.LongValueSink;
import reptor.measr.Meter;


public class LongConcurrentFilteringMeter<S extends LongValueSink>
        implements LongIntervalValueSink, IntervalResult<S>, Meter
{
    private final Stopwatch  m_stopwatch   = new Stopwatch();
    private final S          m_sink;

    private volatile boolean m_isrecording = false;


    public LongConcurrentFilteringMeter(S sink)
    {
        Objects.requireNonNull( sink, "sink" );

        m_sink = sink;
    }


    public boolean isIntervalRunning()
    {
        return m_isrecording;
    }


    @Override
    public void accept(long value)
    {
        if( !isIntervalRunning() )
            return;

        m_sink.accept( value );
    }


    @Override
    public void intervalStarted()
    {
        if( isIntervalRunning() )
            return;

        m_stopwatch.start();
        m_isrecording = true;
    }


    @Override
    public void intervalEnded()
    {
        if( !isIntervalRunning() )
            return;

        m_isrecording = false;
        m_stopwatch.stop();
    }


    @Override
    public void intervalCancelled()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void reset()
    {
        m_stopwatch.reset();
        m_sink.reset();
    }


    @Override
    public S getValueSummary()
    {
        return m_sink;
    }


    @Override
    public long getElapsedTime()
    {
        return m_stopwatch.getElapsedTime();
    }
}
