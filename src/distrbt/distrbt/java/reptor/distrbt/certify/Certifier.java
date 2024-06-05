package reptor.distrbt.certify;

import reptor.chronos.Commutative;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


@Commutative
public interface Certifier extends CertificationHandler
{
    void createCertificate(Data data, MutableData out);
}
