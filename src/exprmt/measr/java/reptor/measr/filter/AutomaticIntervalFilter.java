package reptor.measr.filter;

import reptor.measr.IntervalObserver;


public abstract class AutomaticIntervalFilter extends IntervalFilter
{
    private static enum Phase
    {
        Delay,
        Recording,
        Finished
    }


    private Phase         m_phase    = Phase.Delay;
    private final long    m_delspan;
    private final long    m_recspan;
    private final boolean m_recdelay = false;


    // -- recspan == 0 -> filter all, recspan < 0 -> do not filter after delspan
    public AutomaticIntervalFilter(long delspan, long recspan, IntervalObserver intobs, IntervalObserver recobs)
    {
        super( intobs, recobs );

        if( delspan < 0 )
            throw new IllegalArgumentException( "delints must be greater or egal 0." );

        m_delspan = delspan;
        m_recspan = recspan;
    }


    @Override
    public void intervalStarted()
    {
        processIntervalEdge( false );
    }


    @Override
    public void intervalEnded()
    {
        processIntervalEdge( true );
    }


    @Override
    public void intervalCancelled()
    {
        if( !isIntRunning() )
            return;

        cancelCounting();

        if( isRecording() )
            super.intervalCancelled();
    }


    @SuppressWarnings("unused")
    private void processIntervalEdge(boolean isended)
    {
        if( m_phase == Phase.Finished )
            return;

        if( !isended && !isIntRunning() )
        {
            long curspan = startCounting();

            if( m_phase == Phase.Delay )
            {
                if( curspan >= m_delspan )
                {
                    m_phase = Phase.Recording;
                    startRecording();
                }
                else if( m_recdelay && curspan == 0 )
                {
                    startRecording();
                }
            }
        }
        // -- Do not do anything here if intervalEnded was invoked when there is no running interval.
        else if( isIntRunning() )
        {
            long curspan = countInterval();

            if( m_recdelay && m_phase == Phase.Delay && curspan >= m_delspan )
                stopRecording();

            // -- If delay phase is ended but the interval is explicitly finished,
            // -- the recording does not start until the next call of intervalStarted.
            if( !isended && m_phase == Phase.Delay && curspan >= m_delspan )
            {
                m_phase = Phase.Recording;
                startRecording();
            }
            // -- If the recording is finished, a potential intervalStarted signal is transformed
            // -- into an intervalEnded one.
            else if( m_recspan > 0 && m_phase == Phase.Recording && curspan >= m_delspan + m_recspan )
            {
                m_phase = Phase.Finished;

                super.intervalEnded();

                stopRecording();
                stopCounting();
            }
            else if( isended )
            {
                stopCounting();
            }
        }

        if( isRecording() )
        {
            if( !isended )
                super.intervalStarted();
            else
                super.intervalEnded();
        }
    }


    protected abstract long startCounting();


    protected abstract long countInterval();


    protected abstract void stopCounting();


    protected abstract void cancelCounting();


    protected abstract boolean isIntRunning();
}
