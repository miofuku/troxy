package reptor.chronos.schedule;

import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.orphics.AttachableTask;


public interface GenericSchedulerTask<D extends ChronosDomainContext> extends GenericScheduler<D>, AttachableTask<D>
{

}
