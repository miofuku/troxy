package reptor.distrbt.certify;


public interface SingleAlgorithmCertification<I, K> extends CertificationMethod<I, K>
{
    ProofAlgorithm getProofAlgorithm();

    @Override
    default ProofAlgorithm getCertificationAlgorithm()
    {
        return getProofAlgorithm();
    }

    @Override
    default ProofAlgorithm getVerificationAlgorithm()
    {
        return getProofAlgorithm();
    }
}
