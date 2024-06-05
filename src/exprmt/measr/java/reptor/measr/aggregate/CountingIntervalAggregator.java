package reptor.measr.aggregate;

import java.util.Objects;

import reptor.measr.IntervalObserver;


public abstract class CountingIntervalAggregator implements IntervalObserver
{
    private final IntervalObserver m_observer;

    private int                    m_remint       = 0;
    private int                    m_aggcnt       = 1;
    private boolean                m_isintrunning = false;


    public CountingIntervalAggregator(IntervalObserver observer, int aggcnt)
    {
        this( observer );

        if( aggcnt < 1 )
            throw new IllegalArgumentException( "Aggregation count has to be at least 1." );

        m_aggcnt = aggcnt;
    }


    protected CountingIntervalAggregator(IntervalObserver observer)
    {
        m_observer = Objects.requireNonNull( observer, "observer" );
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
        if( !m_isintrunning )
            return;

        m_isintrunning = false;
        m_remint = m_aggcnt;

        m_observer.intervalCancelled();
    }


    private void processIntervalEdge(boolean isended)
    {
        if( !isended && !m_isintrunning )
        {
            m_isintrunning = true;

            if( m_remint > 0 )
                m_remint--;
            else
            {
                onAggregatedIntervalStarted();

                m_remint = m_aggcnt;
            }
        }
        else if( m_isintrunning )
        {
            assert m_remint > 0;

            m_isintrunning = !isended;
            m_remint--;

            if( m_remint == 0 )
            {
                if( isended )
                    onAggregatedIntervalEnded();
                else
                    onAggregatedIntervalStarted();

                m_remint = m_aggcnt;
            }
        }
    }


    protected void onAggregatedIntervalStarted()
    {
        m_observer.intervalStarted();
    }


    protected void onAggregatedIntervalEnded()
    {
        m_observer.intervalEnded();
    }


    protected int getAggregatorCount()
    {
        return m_aggcnt;
    }


    protected void setAggregatorCount(int value)
    {
        m_aggcnt = value;
    }
}
