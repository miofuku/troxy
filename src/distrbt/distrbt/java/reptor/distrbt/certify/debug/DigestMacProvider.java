package reptor.distrbt.certify.debug;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.CertificateProvider;


public interface DigestMacProvider extends CertificateProvider<ProcessIDHolder>
{

    @Override
    SingleDigestMacFormat       getCertificateFormat();

    DigestMacCertifier          createMessageCertifier(int remid);

    @Override
    default BidirectionalCertifier createCertifier(ProcessIDHolder key)
    {
        return createMessageCertifier( key.getProcessID() );
    }

}
