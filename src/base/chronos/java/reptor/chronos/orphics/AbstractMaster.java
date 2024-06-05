package reptor.chronos.orphics;

import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;

public abstract class AbstractMaster<D extends ChronosDomainContext> extends AbstractTask
                                                                     implements SchedulerContext<D>
{

    public AbstractMaster()
    {

    }


    @Override
    protected abstract SchedulerContext<? extends D> master();


    protected D domain()
    {
        return master().getDomainContext();
    }


    @Override
    public D getDomainContext()
    {
        return domain();
    }

}
