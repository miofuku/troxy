package reptor.distrbt.certify.signature;

import java.security.Signature;

import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import reptor.distrbt.certify.ProofAlgorithm;
import reptor.jlib.hash.HashAlgorithm;


public interface SignatureAlgorithm extends ProofAlgorithm
{

    SignatureType           getKeyType();
    HashAlgorithm           getHashAlgorithm();

    Signature               signer();
    JcaContentSignerBuilder signerBuilder();

}
