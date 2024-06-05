package reptor.measr.meter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import reptor.measr.LongIntervalValueSink;
import reptor.measr.LongValueSink;
import reptor.measr.Meter;


public class LongIntervalMeter<S extends LongValueSink>
        implements LongIntervalValueSink, IntervalResult<S>, Meter
{
    @FunctionalInterface
    public interface Listener<S extends LongValueSink>
    {
        void intervalEnded(IntervalResult<?> meter, int intno, long duration, S sink);
    }

    protected static class IntSum<S extends LongValueSink> implements IntervalResult<S>
    {
        private final S m_sink;
        private long    m_elapsed;


        public IntSum(S sink)
        {
            m_sink = sink;
        }


        @Override
        public long getElapsedTime()
        {
            return m_elapsed;
        }


        @Override
        public S getValueSummary()
        {
            return m_sink;
        }


        protected void reset()
        {
            m_sink.reset();
            m_elapsed = 0;
        }
    }


    private final Stopwatch                       m_stopwatch = new Stopwatch();
    private final Collection<Listener<? super S>> m_listeners;

    private int                                   m_intcnt;
    private IntSum<S>                             m_curint;


    @SafeVarargs
    public LongIntervalMeter(S sink, Listener<? super S>... listeners)
    {
        this( sink, Arrays.asList( listeners ) );
    }


    public LongIntervalMeter(S sink, Collection<Listener<? super S>> listeners)
    {
        Objects.requireNonNull( sink, "sink" );

        m_curint = new IntSum<>( sink );
        m_listeners = new ArrayList<>( listeners );
    }


    public boolean isIntervalRunning()
    {
        return m_stopwatch.isRecording();
    }


    @Override
    public void accept(long value)
    {
        if( isIntervalRunning() )
            m_curint.m_sink.accept( value );
    }


    @Override
    public void intervalStarted()
    {
        if( isIntervalRunning() )
            intervalEnded();

        m_stopwatch.reset();
        m_curint.reset();

        m_stopwatch.start();
    }


    @Override
    public void intervalEnded()
    {
        if( !isIntervalRunning() )
            return;

        m_stopwatch.stop();

        m_curint.m_elapsed = m_stopwatch.getElapsedTime();
        m_intcnt++;

        onIntervalEnded( m_intcnt - 1, m_stopwatch.getElapsedTime(), m_curint.m_sink );
    }


    protected void onIntervalEnded(int intno, long dur, S sink)
    {
        for( Listener<? super S> l : m_listeners )
            l.intervalEnded( this, intno, dur, sink );
    }


    @Override
    public void intervalCancelled()
    {
        if( !isIntervalRunning() )
            return;

        m_stopwatch.reset();
        m_curint.reset();
    }


    @Override
    public void reset()
    {
        m_stopwatch.reset();
        m_curint.reset();
        m_intcnt = 0;
    }


    public int getIntervalCount()
    {
        return m_intcnt;
    }


    @Override
    public S getValueSummary()
    {
        return m_curint.m_sink;
    }


    @Override
    public long getElapsedTime()
    {
        return m_stopwatch.getElapsedTime();
    }


    protected IntSum<S> getCurrentInt()
    {
        return m_curint;
    }


    protected void setCurrentInt(IntSum<S> v)
    {
        m_curint = v;
    }
}
