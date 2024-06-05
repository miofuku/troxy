package reptor.distrbt.certify.mac;

import java.security.Key;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.CertificateProvider;


public interface MacProvider extends CertificateProvider<SharedKeyHolder>
{

    @Override
    SingleMacFormat             getCertificateFormat();

    BidirectionalCertifier      createMessageCertifier(Key key);
    GroupConnectionCertifier    createGroupCertifier(Key key, int grpsize);

    @Override
    default BidirectionalCertifier createCertifier(SharedKeyHolder key)
    {
        return createMessageCertifier( key.getSharedKey() );
    }

}
