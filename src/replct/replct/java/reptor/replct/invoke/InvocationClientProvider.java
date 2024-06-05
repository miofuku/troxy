package reptor.replct.invoke;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.invoke.bft.ProphecySketcher;


public interface InvocationClientProvider
{
    InvocationClientHandler createInvocationHandler(SchedulerContext<? extends SelectorDomainContext> master, short clino, ProphecySketcher sketcher);
}
