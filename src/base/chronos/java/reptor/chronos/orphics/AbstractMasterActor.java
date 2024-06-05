package reptor.chronos.orphics;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;


public abstract class AbstractMasterActor<M, D extends ChronosDomainContext, S extends SchedulerContext<? extends D>>
                                extends AbstractMaster<D>
                                implements Actor, PushMessageSink<M>, SchedulerContext<D>
{

    protected final S          m_master;
    protected final Queue<M>   m_inqueue;

    public AbstractMasterActor(S master)
    {
        m_master  = Objects.requireNonNull( master );
        m_inqueue = inQueue();
    }


    @Override
    protected S master()
    {
        return m_master;
    }


    protected Queue<M> inQueue()
    {
        return new ArrayDeque<>();
    }


    @Override
    public void enqueueMessage(M msg)
    {
        m_inqueue.add( msg );

        notifyReady();
    }


    @Override
    public boolean execute()
    {
        do
        {
            M msg;

            while( ( msg = m_inqueue.poll() )!=null )
                processMessage( msg );

            executeSubjects();
        }
        while( !m_inqueue.isEmpty() );

        clearReady();

        return true;
    }


    protected abstract void processMessage(M msg);


    protected abstract void executeSubjects();

}
