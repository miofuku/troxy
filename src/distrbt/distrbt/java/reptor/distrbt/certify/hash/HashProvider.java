package reptor.distrbt.certify.hash;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.CertificateProvider;


public interface HashProvider extends CertificateProvider<Object>
{

    @Override
    SingleHashFormat    getCertificateFormat();

    HashCertifier       createMessageCertifier();

    @Override
    default BidirectionalCertifier createCertifier(Object key)
    {
        return createMessageCertifier();
    }

}
