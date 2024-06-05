package reptor.distrbt.certify.debug;

import java.security.MessageDigest;

import reptor.distrbt.certify.ProofAlgorithm;
import reptor.jlib.hash.HashAlgorithm;


public interface DigestMacAlgorithm extends ProofAlgorithm
{

    HashAlgorithm getHashAlgorithm();

    MessageDigest digester();

}
