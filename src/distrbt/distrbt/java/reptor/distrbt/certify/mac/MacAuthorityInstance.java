package reptor.distrbt.certify.mac;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.CertificationAuthorityInstance;

@Commutative
public interface MacAuthorityInstance extends CertificationAuthorityInstance
{
    MacProvider createMacProvider(SingleMacFormat certformat);
}
