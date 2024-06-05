package reptor.distrbt.certify.mac;

import java.util.Objects;

import reptor.distrbt.certify.BidirectionalCertification;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.distrbt.certify.SingleAlgorithmCertification;

public class AuthenticatorCertification<I, K> implements SingleAlgorithmCertification<I, K>
{

    private final BidirectionalCertification<I, K> m_certmethod;


    public AuthenticatorCertification(BidirectionalCertification<I, K> certmethod)
    {
        m_certmethod = Objects.requireNonNull( certmethod );
    }


    @Override
    public String toString()
    {
        return Authenticating.authenticatorName( m_certmethod.toString() );
    }


    @Override
    public ProofAlgorithm getProofAlgorithm()
    {
        return m_certmethod.getProofAlgorithm();
    }


    @Override
    public AuthenticatorProvider<? super K> createCertificationProvider(I insts)
    {
        return new AuthenticatorProvider<K>( m_certmethod.createCertificationProvider( insts ) );
    }

}
