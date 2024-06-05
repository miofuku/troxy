package reptor.distrbt.certify.hash;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleHashFormat extends SingleProofFormat, HashCertification
{
    @Override
    HashProofAlgorithm getProofAlgorithm();

    @Override
    default HashProvider createCertificationProvider(HashAuthorityInstance authinst)
    {
        return authinst.createHashProvider( this );
    }

    @Override
    default HashProvider createCertificationProvider(HashAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getHashAuthorityInstance() );
    }

    @Override
    default SingleHashFormat getCertificateFormat()
    {
        return this;
    }
}
