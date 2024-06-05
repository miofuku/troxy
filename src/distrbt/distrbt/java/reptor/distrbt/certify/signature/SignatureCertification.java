package reptor.distrbt.certify.signature;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertification;


@Immutable
public interface SignatureCertification extends BidirectionalCertification<SignatureAuthorityInstanceHolder, PublicKeyHolder>
{

    @Override
    SignatureAlgorithm getProofAlgorithm();

    SignatureProvider  createCertificationProvider(SignatureAuthorityInstance authinst);

    @Override
    default SignatureProvider createCertificationProvider(SignatureAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getSignatureAuthorityInstance() );
    }

}
