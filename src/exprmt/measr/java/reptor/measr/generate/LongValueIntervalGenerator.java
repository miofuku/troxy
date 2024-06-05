package reptor.measr.generate;

import java.util.Objects;

import reptor.measr.IntervalObserver;
import reptor.measr.LongValueSink;


public class LongValueIntervalGenerator implements LongValueSink
{
    private final LongValueSink    m_sink;
    private final IntervalObserver m_intobs;
    private final boolean          m_genendsignal;


    public LongValueIntervalGenerator(LongValueSink sink, IntervalObserver intobs, boolean genendsignal)
    {
        m_sink = sink != null ? sink : LongValueSink.EMPTY;
        m_intobs = Objects.requireNonNull( intobs, "intobs" );
        m_genendsignal = genendsignal;
    }


    @Override
    public void accept(long value)
    {
        m_intobs.intervalStarted();

        m_sink.accept( value );

        if( m_genendsignal )
            m_intobs.intervalEnded();
    }


    @Override
    public void reset()
    {
        m_sink.reset();
        m_intobs.intervalCancelled();
    }
}
