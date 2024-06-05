package reptor.distrbt.certify.trusted;

import java.util.Objects;

import reptor.jlib.hash.HashAlgorithm;


public class TrustedMacCertification implements TrustedCertification
{

    private final SingleTrustedMacFormat m_certformat;


    public TrustedMacCertification(SingleTrustedMacFormat certformat)
    {
        m_certformat = Objects.requireNonNull( certformat );
    }


    public TrustedMacCertification(TrustedAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        this( new PlainSingleTrustedMacFormat( proofalgo, digalgo ) );
    }


    @Override
    public String toString()
    {
        return m_certformat.toString();
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


    public TrustedMacProvider createCertificationProvider(Trinx trinx)
    {
        return new TrustedMacProvider( trinx, m_certformat );
    }


    public TrustedMacProvider createCertificationProvider(TrustedAuthorityInstance authinst)
    {
        return authinst.createTrustedMacProvider( m_certformat );
    }


    @Override
    public TrustedMacProvider createCertificationProvider(TrustedAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getTrustedAuthorityInstance() );
    }

}
