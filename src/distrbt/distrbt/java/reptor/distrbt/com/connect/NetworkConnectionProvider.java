package reptor.distrbt.com.connect;

import java.util.function.IntFunction;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;
import reptor.distrbt.domains.SelectorDomainContext;


@Commutative
public interface NetworkConnectionProvider extends Orphic
{
    PushNetworkTransmissionConnection connection(SelectorDomainContext domcntxt, int connid, IntFunction<Object> msgcntxtfac);
}
