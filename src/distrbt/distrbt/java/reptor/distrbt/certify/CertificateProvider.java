package reptor.distrbt.certify;

import reptor.chronos.Commutative;

@Commutative
public interface CertificateProvider<K> extends CertificationProvider<K>
{
    CertificateFormat       getCertificateFormat();
    BidirectionalCertifier  createCertifier(K key);
}
