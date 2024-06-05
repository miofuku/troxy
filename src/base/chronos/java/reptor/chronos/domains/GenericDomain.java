package reptor.chronos.domains;

import reptor.chronos.ChronosDomain;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.schedule.GenericScheduler;


public interface GenericDomain<D extends ChronosDomainContext> extends ChronosDomain, GenericScheduler<D>
{

}
