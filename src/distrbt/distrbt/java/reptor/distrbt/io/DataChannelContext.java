package reptor.distrbt.io;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.MasterContext;


public interface DataChannelContext<D extends ChronosDomainContext> extends MasterContext<D>
{
    void    dataReady(SynchronousLinkElement elem);

    String  getChannelName();
}
