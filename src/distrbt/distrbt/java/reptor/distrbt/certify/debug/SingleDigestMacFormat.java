package reptor.distrbt.certify.debug;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleDigestMacFormat extends SingleProofFormat, DigestMacCertification
{
    @Override
    DigestMacAlgorithm getProofAlgorithm();

    default DigestMacProvider createCertificationProvider(DigestMacAuthorityInstance authinst)
    {
        return authinst.createDigestMacProvider( this );
    }

    @Override
    default DigestMacProvider createCertificationProvider(DigestMacAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getDigestMacAuthorityInstance() );
    }

    @Override
    default SingleDigestMacFormat getCertificateFormat()
    {
        return this;
    }
}
