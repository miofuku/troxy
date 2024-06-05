package reptor.distrbt.certify.hash;

import java.security.MessageDigest;

import reptor.distrbt.certify.ProofAlgorithm;
import reptor.jlib.hash.HashAlgorithm;


public interface HashProofAlgorithm extends ProofAlgorithm
{

    HashAlgorithm getHashAlgorithm();

    MessageDigest digester();

}
