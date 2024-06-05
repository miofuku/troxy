package reptor.distrbt.certify.debug;

import java.util.Objects;

import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.ProofAlgorithm;

public class WrappedCertification<I, K> implements CertificationMethod<I, K>
{

    private final CertificationMethod<I, ? super K> m_certmethod;
    private final boolean                           m_docertify;
    private final boolean                           m_doverify;
    private final boolean                           m_forcevalid;


    public WrappedCertification(CertificationMethod<I, ? super K> certmethod,
                                boolean docertify, boolean doverify, boolean forcevalid)
    {
        m_certmethod = Objects.requireNonNull( certmethod );
        m_docertify  = docertify;
        m_doverify   = doverify;
        m_forcevalid = forcevalid;
    }


    @Override
    public ProofAlgorithm getCertificationAlgorithm()
    {
        return m_certmethod.getCertificationAlgorithm();
    }


    @Override
    public ProofAlgorithm getVerificationAlgorithm()
    {
        return m_certmethod.getVerificationAlgorithm();
    }


    @Override
    public CertificationProvider<? super K> createCertificationProvider(I insts)
    {
        CertificationProvider<? super K> provider = m_certmethod.createCertificationProvider( insts );

        return new WrappedCertificationProvider<>( provider, m_docertify, m_doverify, m_forcevalid );
    }


    @Override
    public String toString()
    {
        String cert = m_docertify ? "c" : "x";
        String verf = m_doverify ? (m_forcevalid ? "v!" : "v") : "x";

        return Certifiying.modifierName( m_certmethod, Certifiying.compoundMethod( cert, verf ) );
    }

}
