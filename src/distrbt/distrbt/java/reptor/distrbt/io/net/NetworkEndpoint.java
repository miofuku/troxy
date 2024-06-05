package reptor.distrbt.io.net;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousEndpoint;
import reptor.chronos.com.SynchronousSink;
import reptor.chronos.com.SynchronousSource;
import reptor.chronos.orphics.AttachableOrphic;
import reptor.distrbt.domains.SelectorDomainContext;


public interface NetworkEndpoint extends CommunicationStage<SynchronousSink, SynchronousSource>, SynchronousEndpoint,
                                         AttachableOrphic<NetworkEndpointContext<? extends SelectorDomainContext>>
{
    // Enables and disabled notifications about ready state but not the endpoint as such.
    void    enable();
    void    disable();
}
