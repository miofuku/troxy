package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.ProofAlgorithm;


public interface TrustedAlgorithm extends ProofAlgorithm
{

    ProofAlgorithm getBaseAlgorithm();

}
