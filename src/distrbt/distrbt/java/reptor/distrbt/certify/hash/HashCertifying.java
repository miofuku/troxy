package reptor.distrbt.certify.hash;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.Set;

import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofType;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;


public class HashCertifying extends Certifiying
{

    public static final ProofType HASH = proofType( "HASH", true, false, false, false );

    public static final HashProofAlgorithm HASH_MD5    = hashProofAlgorithm( Hashing.MD5 );
    public static final HashProofAlgorithm HASH_SHA1   = hashProofAlgorithm( Hashing.SHA1 );
    public static final HashProofAlgorithm HASH_SHA256 = hashProofAlgorithm( Hashing.SHA256 );


    public static HashProofAlgorithm tryParseHashProofAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "HASH_SHA256":
        case "SHA256":
            return HASH_SHA256;
        case "HASH_SHA1":
        case "SHA1":
            return HASH_SHA1;
        case "HASH_MD5":
        case "MD5":
            return HASH_MD5;
        default:
            return null;
        }
    }


    public static HashProofAlgorithm hashProofAlgorithm(HashAlgorithm hashalgo)
    {
        return new HashProofAlgorithm()
        {
            @Override
            public String toString()
            {
                return getName();
            }

            @Override
            public String getName()
            {
                return algorithmName( HASH, hashalgo );
            }

            @Override
            public ProofType getProofType()
            {
                return HASH;
            }

            @Override
            public int getMaximumProofSize()
            {
                return hashalgo.getHashSize();
            }

            @Override
            public Set<KeyType> getRequiredKeyTypes()
            {
                return Collections.emptySet();
            }

            @Override
            public HashAlgorithm getHashAlgorithm()
            {
                return hashalgo;
            }

            @Override
            public MessageDigest digester()
            {
                return hashalgo.digester();
            }
        };
    }

}
