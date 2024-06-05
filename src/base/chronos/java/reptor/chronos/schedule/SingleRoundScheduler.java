package reptor.chronos.schedule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.ChronosDomainContext;


public class SingleRoundScheduler<D extends ChronosDomainContext> extends AbstractGenericSchedulerTask<D>
{

    private final ArrayList<ChronosTask>        m_tasks      = new ArrayList<ChronosTask>();
    private final ArrayDeque<ChronosTask>       m_readytasks = new ArrayDeque<>();


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
        m_readytasks.add( task );

        notifyReady();
    }


    @Override
    public boolean execute()
    {
        return isDone( execute( m_readytasks ) );
    }


    public static <T extends ChronosTask> boolean execute(Deque<T> readytasks)
    {
        T lasttask = readytasks.peekLast();

        if( lasttask==null )
            return true;

        while( true )
        {
            T task = readytasks.poll();

            // TODO: If unfinished tasks are directly re-appended to the ready list, they are re-executed
            //       before tasks that are unblocked due to the network or an expired timers.
            //       Is that fair?
            if( !task.execute() )
                readytasks.add( task );

            if( task==lasttask )
                break;
        }

        return readytasks.isEmpty();
    }

}
