package reptor.distrbt.certify;

import reptor.chronos.Commutative;

@Commutative
public interface BidirectionalCertifier extends Certifier, Verifier, ConnectionCertifier
{
    @Override
    default Certifier getCertifier()
    {
        return this;
    }

    @Override
    default Verifier getVerifier()
    {
        return this;
    }
}
