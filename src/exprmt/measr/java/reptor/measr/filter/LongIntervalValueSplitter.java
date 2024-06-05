package reptor.measr.filter;

import java.util.Objects;

import reptor.measr.IntervalObserver;
import reptor.measr.LongIntervalValueSink;
import reptor.measr.LongValueSink;


public class LongIntervalValueSplitter implements LongIntervalValueSink
{
    private final LongValueSink    m_valsink;
    private final IntervalObserver m_intobs;


    public LongIntervalValueSplitter(LongValueSink valsink, IntervalObserver intobs)
    {
        m_valsink = Objects.requireNonNull( valsink, "valsink" );
        m_intobs = Objects.requireNonNull( intobs, "intobs" );
    }


    @Override
    public void accept(long value)
    {
        m_valsink.accept( value );
    }


    @Override
    public void reset()
    {
        m_valsink.reset();
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
        m_intobs.intervalCancelled();
    }
}
