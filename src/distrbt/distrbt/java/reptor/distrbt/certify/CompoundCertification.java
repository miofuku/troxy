package reptor.distrbt.certify;

import java.util.Objects;

import reptor.chronos.Immutable;


@Immutable
public class CompoundCertification<I, K> implements CertificationMethod<I, K>
{

    private final CertificationMethod<? super I, ? super K> m_certmethod;
    private final CertificationMethod<? super I, ? super K> m_verfmethod;

    public CompoundCertification(CertificationMethod<? super I, ? super K> certmethod,
                                   CertificationMethod<? super I, ? super K> verfmethod)
    {
        m_certmethod = Objects.requireNonNull( certmethod );
        m_verfmethod = Objects.requireNonNull( verfmethod );
    }

    @Override
    public String toString()
    {
        return Certifiying.compoundName( m_certmethod.toString(), m_verfmethod.toString() );
    }

    @Override
    public ProofAlgorithm getCertificationAlgorithm()
    {
        return m_certmethod.getCertificationAlgorithm();
    }

    @Override
    public ProofAlgorithm getVerificationAlgorithm()
    {
        return m_verfmethod.getVerificationAlgorithm();
    }

    @Override
    public CertificationProvider<? super K> createCertificationProvider(I insts)
    {
        CertificationProvider<? super K> certprov = m_certmethod.createCertificationProvider( insts );
        CertificationProvider<? super K> verfprov = m_verfmethod.createCertificationProvider( insts );

        return new CompoundCertificationProvider<>( certprov, verfprov );
    }

}
