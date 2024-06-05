package reptor.distrbt.certify.trusted;

import java.util.Objects;

import reptor.jlib.hash.HashAlgorithm;


public class TrustedCounterCertification implements TrustedCertification
{

    private final SingleTrustedMacFormat m_certformat;
    private final int                    m_ctrno;


    public TrustedCounterCertification(SingleTrustedMacFormat certformat, int ctrno)
    {
        m_certformat = Objects.requireNonNull( certformat );
        m_ctrno      = ctrno;
    }


    public TrustedCounterCertification(TrustedAlgorithm proofalgo, HashAlgorithm digalgo, int ctrno)
    {
        this( new PlainSingleTrustedMacFormat( proofalgo, digalgo ), ctrno );
    }


    @Override
    public SingleTrustedMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public TrustedAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


   public TrustedCounterProvider createCertificationProvider(Trinx trinx)
    {
        return new TrustedCounterProvider( trinx, m_certformat, m_ctrno );
    }


    public TrustedCounterProvider createCertificationProvider(TrustedAuthorityInstance authinst)
    {
        return authinst.createTrustedCounterProvider( m_certformat, m_ctrno );
    }


    @Override
    public TrustedCounterProvider createCertificationProvider(TrustedAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getTrustedAuthorityInstance() );
    }

}
