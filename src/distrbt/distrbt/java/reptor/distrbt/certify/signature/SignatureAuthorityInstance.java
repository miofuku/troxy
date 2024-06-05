package reptor.distrbt.certify.signature;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.CertificationAuthorityInstance;

@Commutative
public interface SignatureAuthorityInstance extends CertificationAuthorityInstance
{
    SignatureProvider createSignatureProvider(SingleSignatureFormat certformat);
}
