package reptor.distrbt.certify.hash;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.CertificationAuthorityInstance;


@Commutative
public interface HashAuthorityInstance extends CertificationAuthorityInstance
{
    HashProvider createHashProvider(SingleHashFormat certformat);
}
