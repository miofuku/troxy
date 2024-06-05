package reptor.distrbt.certify.mac;

import reptor.distrbt.certify.CertificateFormat;


public interface UniformAuthenticatorFormat extends CertificateFormat
{

    int               getNumberOfProcesses();
    CertificateFormat getBaseFormat();

}
