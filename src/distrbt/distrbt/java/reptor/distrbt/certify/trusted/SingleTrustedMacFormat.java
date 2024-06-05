package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleTrustedMacFormat extends SingleProofFormat
{
    @Override
    TrustedAlgorithm getProofAlgorithm();
}
