package reptor.chronos.orphics;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.SchedulerContext;

public abstract class AbstractTask implements ChronosTask
{

    private boolean m_isready = false;


    public AbstractTask()
    {

    }


    protected abstract SchedulerContext<?> master();


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    protected void markReady()
    {
        m_isready = true;
    }


    protected void notifyReady()
    {
        if( !m_isready )
        {
            markReady();
            master().taskReady( this );
        }
    }


    protected void clearReady()
    {
        m_isready = false;
    }


    protected boolean isDone(boolean isdone)
    {
        if( isdone )
            clearReady();

        return isdone;
    }

}
