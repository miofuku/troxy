package reptor.chronos.com;

import reptor.chronos.ChronosAddress;

public interface DomainEndpoint<C>
{
    C   createChannel(ChronosAddress origin);
}
