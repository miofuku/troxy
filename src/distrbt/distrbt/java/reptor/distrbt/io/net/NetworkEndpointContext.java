package reptor.distrbt.io.net;

import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;


public interface NetworkEndpointContext<D extends SelectorDomainContext> extends DataChannelContext<D>
{
    void    endpointActivated(NetworkEndpoint elem);
}
