package reptor.distrbt.certify.signature;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleSignatureFormat extends SingleProofFormat, SignatureCertification
{
    @Override
    SignatureAlgorithm getProofAlgorithm();

    default SignatureProvider createCertificationProvider(SignatureAuthorityInstance authinst)
    {
        return authinst.createSignatureProvider( this );
    }

    @Override
    default SignatureProvider createCertificationProvider(SignatureAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getSignatureAuthorityInstance() );
    }

    @Override
    default SingleSignatureFormat getCertificateFormat()
    {
        return this;
    }
}
