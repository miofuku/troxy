package reptor.chronos.orphics;

import reptor.chronos.ChronosTask;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;

public interface AttachableTask<D extends ChronosDomainContext>
        extends AttachableOrphic<SchedulerContext<? extends D>>, ChronosTask
{
}
