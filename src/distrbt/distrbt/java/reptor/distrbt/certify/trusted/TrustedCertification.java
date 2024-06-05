package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.BidirectionalCertification;


public interface TrustedCertification extends BidirectionalCertification<TrustedAuthorityInstanceHolder, TssIDHolder>
{
    @Override
    TrustedAlgorithm getProofAlgorithm();
}
