package reptor.chronos.context;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomain;
import reptor.chronos.Commutative;
import reptor.chronos.Immutable;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;

@Commutative
public interface ChronosDomainContext extends DomainEndpoint<PushMessageSink<Portal<?>>>
{
    @Immutable
    ChronosAddress  getDomainAddress();

    long            time();
    TimeKey         registerTimer(TimerHandler handler);

    boolean         checkDomain();
    ChronosDomain   getCurrentDomain();
}
