package reptor.distrbt.certify;

import reptor.chronos.Commutative;

@Commutative
public interface GroupConnectionCertifier extends VerifierGroup
{
    Certifier getCertifier();
    @Override
    Verifier  getVerifier(int index);
}
