package reptor.distrbt.certify;

import reptor.chronos.Immutable;
import reptor.chronos.Orphic;
import reptor.jlib.hash.HashAlgorithm;


@Immutable
public interface CertificationHandler extends Orphic
{

    int               getCertificateSize();
    boolean           requiresDigestedData();
    HashAlgorithm     getDigestAlgorithm();

    CertificateFormat getCertificateFormat();

}
