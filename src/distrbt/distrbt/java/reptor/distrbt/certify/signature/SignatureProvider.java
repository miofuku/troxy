package reptor.distrbt.certify.signature;

import java.security.PublicKey;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.CertificateProvider;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;


public interface SignatureProvider extends CertificateProvider<PublicKeyHolder>
{

    @Override
    SingleSignatureFormat   getCertificateFormat();

    Certifier               createSignatureCertifier();
    Verifier                createSignatureVerifier(PublicKey pubkey);
    BidirectionalCertifier  createMessageCertifier(PublicKey pubkey);

    @Override
    default BidirectionalCertifier createCertifier(PublicKeyHolder key)
    {
        return createMessageCertifier( key.getPublicKey() );
    }

}
