package reptor.distrbt.certify;

import reptor.chronos.ImmutableObject;
import reptor.jlib.hash.HashAlgorithm;


public interface CertificateFormat extends ImmutableObject
{

    HashAlgorithm getDigestAlgorithm();

    int getCertificateSize();

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    default String methodName(String authority)
    {
        return Certifiying.methodName( authority, this );
    }

}
