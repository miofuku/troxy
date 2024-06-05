package reptor.distrbt.certify;

import reptor.chronos.Commutative;
import reptor.distrbt.common.data.Data;


@Commutative
public interface Verifier extends CertificationHandler
{
    boolean verifyCertificate(Data data, Data certdata);
}
