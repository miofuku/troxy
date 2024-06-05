package reptor.chronos.schedule;

import java.util.ArrayList;
import java.util.List;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.ChronosDomainContext;


public class ScanScheduler<D extends ChronosDomainContext> extends AbstractGenericSchedulerTask<D>
{

    private final ArrayList<ChronosTask>    m_tasks = new ArrayList<ChronosTask>();

    private boolean m_isdone;


     @Override
    public void registerTask(ChronosTask task)
    {
        m_tasks.add( task );
    }


    @Override
    public List<ChronosTask> listSubordinates()
    {
        return m_tasks;
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        m_isdone = false;

        notifyReady();
    }


    @Override
    public boolean execute()
    {
        m_isdone = true;

        for( int i=0, n=m_tasks.size(); i<n; i++ )
        {
            ChronosTask task = m_tasks.get( i );

            if( task.isReady() )
                m_isdone = task.execute() && m_isdone;
        }

        return isDone( m_isdone );
    }

}
