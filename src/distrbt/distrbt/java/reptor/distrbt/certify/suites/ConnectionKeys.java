package reptor.distrbt.certify.suites;

import reptor.distrbt.certify.debug.ProcessIDHolder;
import reptor.distrbt.certify.mac.SharedKeyHolder;
import reptor.distrbt.certify.signature.PublicKeyHolder;
import reptor.distrbt.certify.trusted.TssIDHolder;


public interface ConnectionKeys extends ProcessIDHolder, SharedKeyHolder, PublicKeyHolder, TssIDHolder
{

}
