package reptor.distrbt.certify.mac;

import java.security.Key;

import javax.crypto.Mac;

import reptor.distrbt.certify.ProofAlgorithm;
import reptor.jlib.hash.HashAlgorithm;


public interface MacAlgorithm extends ProofAlgorithm
{

    HashAlgorithm getHashAlgorithm();

    Mac macCreator();
    Mac macCreator(String secret);
    Mac macCreator(Key key);

}
