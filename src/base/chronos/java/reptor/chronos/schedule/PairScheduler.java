package reptor.chronos.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.ChronosDomainContext;


public class PairScheduler<D extends ChronosDomainContext> extends AbstractGenericSchedulerTask<D>
{

    private ChronosTask     m_task0 = null;
    private ChronosTask     m_task1 = null;


    @Override
    public void registerTask(ChronosTask task)
    {
        if( m_task0==null )
            m_task0 = Objects.requireNonNull( task );
        else if( m_task1==null )
            m_task1 = Objects.requireNonNull( task );
        else
            throw new IndexOutOfBoundsException();
    }


    @Override
    public List<ChronosTask> listSubordinates()
    {
        return Arrays.asList( m_task0, m_task1 );
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( m_task0.isReady() )
            m_task0.execute();

        if( m_task1.isReady() )
            m_task1.execute();

        return isDone( !m_task0.isReady() && !m_task1.isReady() );
    }

}
