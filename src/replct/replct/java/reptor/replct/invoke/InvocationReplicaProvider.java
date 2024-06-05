package reptor.replct.invoke;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.domains.SelectorDomainContext;


public interface InvocationReplicaProvider
{
    InvocationReplicaHandler createHandler(SchedulerContext<? extends SelectorDomainContext> master,
                                                     short clintshard, int wrkno);
}
