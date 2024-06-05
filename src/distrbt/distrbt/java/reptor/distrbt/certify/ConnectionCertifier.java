package reptor.distrbt.certify;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;

@Commutative
public interface ConnectionCertifier extends Orphic
{
    Certifier getCertifier();
    Verifier  getVerifier();
}
