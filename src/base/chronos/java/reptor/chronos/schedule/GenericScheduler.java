package reptor.chronos.schedule;

import reptor.chronos.ChronosTask;
import reptor.chronos.Explorable;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AttachableTask;


public interface GenericScheduler<D extends ChronosDomainContext> extends Explorable
{

    SchedulerContext<D> getContext();

    void                registerTask(ChronosTask task);

    default void bindTask(AttachableTask<? super D> task)
    {
        task.bindToMaster( getContext() );
        registerTask( task );
    }

}
