package reptor.distrbt.certify.trusted;

import java.util.Objects;


public class SequentialCounterCertification implements TrustedCertification
{

    private final SingleCounterFormat m_certformat;
    private final int                 m_ctrno;


    public SequentialCounterCertification(SingleCounterFormat certformat, int ctrno)
    {
        m_certformat = Objects.requireNonNull( certformat );
        m_ctrno      = ctrno;
    }


    @Override
    public SingleCounterFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public TrustedAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


    public SequentialCounterProvider createCertificationProvider(Trinx trinx)
    {
        return new SequentialCounterProvider( trinx, m_certformat, m_ctrno );
    }


    public SequentialCounterProvider createCertificationProvider(TrustedAuthorityInstance authinst)
    {
        return authinst.createSequentialCounterProvider( m_certformat, m_ctrno );
    }


    @Override
    public SequentialCounterProvider createCertificationProvider(TrustedAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getTrustedAuthorityInstance() );
    }

}
