package reptor.distrbt.certify.debug;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.CertificationAuthorityInstance;


@Commutative
public interface DigestMacAuthorityInstance extends CertificationAuthorityInstance
{
    DigestMacProvider createDigestMacProvider(SingleDigestMacFormat certformat);
}
