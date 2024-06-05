package reptor.chronos.context;

import reptor.chronos.ChronosTask;

public interface SchedulerContext<D extends ChronosDomainContext> extends MasterContext<D>
{
    void taskReady(ChronosTask task);
}
