package reptor.measr.filter;

import reptor.measr.IntervalObserver;


public abstract class IntervalFilter implements IntervalObserver
{
    @FunctionalInterface
    public interface Factory<T extends IntervalFilter>
    {
        T create(IntervalObserver intobs, IntervalObserver recobs);
    }


    private final IntervalObserver m_recobs;
    private final IntervalObserver m_intobs;

    private boolean                m_isrecording = false;


    public IntervalFilter(IntervalObserver intobs, IntervalObserver recobs)
    {
        if( intobs == null && recobs == null )
            throw new IllegalArgumentException( "At least one observer has to be passed." );

        m_intobs = intobs != null ? intobs : IntervalObserver.EMPTY;
        m_recobs = recobs != null ? recobs : IntervalObserver.EMPTY;
    }


    @Override
    public void intervalStarted()
    {
        if( !m_isrecording )
            return;

        m_intobs.intervalStarted();
    }


    @Override
    public void intervalEnded()
    {
        if( !m_isrecording )
            return;

        m_intobs.intervalEnded();
    }


    @Override
    public void intervalCancelled()
    {
        if( !m_isrecording )
            return;

        m_intobs.intervalCancelled();
    }


    public boolean isRecording()
    {
        return m_isrecording;
    }


    protected void startRecording()
    {
        m_isrecording = true;

        m_recobs.intervalStarted();
    }


    protected void stopRecording()
    {
        m_isrecording = false;

        m_recobs.intervalEnded();
    }
}
