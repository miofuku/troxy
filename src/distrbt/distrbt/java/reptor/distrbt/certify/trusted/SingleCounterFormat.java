package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleCounterFormat extends SingleProofFormat
{
    @Override
    TrustedAlgorithm getProofAlgorithm();

    int              getCounterOffset();
}
