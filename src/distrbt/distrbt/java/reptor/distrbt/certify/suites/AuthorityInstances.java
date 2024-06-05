package reptor.distrbt.certify.suites;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.debug.DigestMacAuthorityInstanceHolder;
import reptor.distrbt.certify.hash.HashAuthorityInstanceHolder;
import reptor.distrbt.certify.mac.MacAuthorityInstanceHolder;
import reptor.distrbt.certify.signature.SignatureAuthorityInstanceHolder;
import reptor.distrbt.certify.trusted.TrustedAuthorityInstanceHolder;


@Commutative
public interface AuthorityInstances extends HashAuthorityInstanceHolder,
                                            DigestMacAuthorityInstanceHolder,
                                            MacAuthorityInstanceHolder,
                                            SignatureAuthorityInstanceHolder,
                                            TrustedAuthorityInstanceHolder
{

}
