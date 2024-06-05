package reptor.measr.generate;

import java.util.Objects;

import reptor.measr.IntervalObserver;
import reptor.measr.LongIntervalValueSink;
import reptor.measr.LongValueSink;


public class LongIntervalValueSynchronizer implements LongIntervalValueSink
{
    private final LongValueSink    m_valsnk;
    private final IntervalObserver m_intobs;


    public LongIntervalValueSynchronizer(LongIntervalValueSink sink)
    {
        this( sink, sink );
    }


    public LongIntervalValueSynchronizer(LongValueSink valsnk, IntervalObserver intobs)
    {
        m_valsnk = Objects.requireNonNull( valsnk, "valsnk" );
        m_intobs = Objects.requireNonNull( intobs, "intobs" );
    }


    @Override
    public void intervalStarted()
    {
        synchronized( m_valsnk )
        {
            m_intobs.intervalStarted();
        }
    }


    @Override
    public void intervalEnded()
    {
        synchronized( m_valsnk )
        {
            m_intobs.intervalEnded();
        }
    }


    @Override
    public void intervalCancelled()
    {
        synchronized( m_valsnk )
        {
            m_intobs.intervalCancelled();
        }
    }


    @Override
    public void accept(long value)
    {
        synchronized( m_valsnk )
        {
            m_valsnk.accept( value );
        }
    }


    @Override
    public void reset()
    {
        synchronized( m_valsnk )
        {
            m_valsnk.reset();
        }
    }
}
