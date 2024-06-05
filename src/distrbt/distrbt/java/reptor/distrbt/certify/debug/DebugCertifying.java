package reptor.distrbt.certify.debug;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofType;
import reptor.distrbt.certify.trusted.TrustedAlgorithm;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.jlib.hash.AbstractHashAlgorithm;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;

import com.google.common.base.Preconditions;


public class DebugCertifying extends Certifiying
{

    public static final HashAlgorithm DUMMY_MD5    = dummyHashAlgorithm( Hashing.MD5 );
    public static final HashAlgorithm DUMMY_SHA1   = dummyHashAlgorithm( Hashing.SHA1 );
    public static final HashAlgorithm DUMMY_SHA256 = dummyHashAlgorithm( Hashing.SHA256 );

    public static final ProofType DIGEST = proofType( "DMAC", true, false, false, false );

    public static final KeyType PROCESS_ID = new KeyType() {};

    public static final DigestMacAlgorithm DMAC_MD5    = digestMacAlgorithm( Hashing.MD5 );
    public static final DigestMacAlgorithm DMAC_SHA1   = digestMacAlgorithm( Hashing.SHA1 );
    public static final DigestMacAlgorithm DMAC_SHA256 = digestMacAlgorithm( Hashing.SHA256 );

    public static final TrustedAlgorithm TMAC_DMAC_SHA256 = TrustedCertifying.trustedAlgorithm( TrustedCertifying.TRUSTED_MAC, DMAC_SHA256 );
    public static final TrustedAlgorithm TCTR_DMAC_SHA256 = TrustedCertifying.trustedAlgorithm( TrustedCertifying.TRUSTED_COUNTER, DMAC_SHA256 );


    public HashAlgorithm tryParseHashAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "DUMMY_SHA256":
            return DUMMY_SHA256;
        case "DUMMY_SHA1":
            return DUMMY_SHA1;
        case "DUMMY_MD5":
            return DUMMY_MD5;
        default:
            return Hashing.tryParseHashAlgorithm( s );
        }
    }


    public static DigestMacAlgorithm tryParseDigestMacAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "DMAC_SHA256":
            return DMAC_SHA256;
        case "DMAC_SHA1":
            return DMAC_SHA1;
        case "DMAC_MD5":
            return DMAC_MD5;
        default:
            return null;
        }
    }


    public static HashAlgorithm dummyHashAlgorithm(HashAlgorithm basealgo)
    {
        return dummyHashAlgorithm( "DUMMY_" + basealgo.getName(), basealgo.getHashSize() );
    }


    public static HashAlgorithm dummyHashAlgorithm(String name, int hashsize)
    {
        Objects.requireNonNull( name );
        Preconditions.checkArgument( hashsize>0 );

        return new AbstractHashAlgorithm()
        {
            @Override
            public String toString()
            {
                return name;
            }

            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public int getHashSize()
            {
                return hashsize;
            }

            @Override
            public MessageDigest digester()
            {
                return new DummyDigest( hashsize );
            }
        };
    }


    public static DigestMacAlgorithm digestMacAlgorithm(HashAlgorithm hashalgo)
    {
        return new DigestMacAlgorithm()
        {
            @Override
            public String toString()
            {
                return getName();
            }

            @Override
            public String getName()
            {
                return algorithmName( DIGEST, hashalgo );
            }

            @Override
            public ProofType getProofType()
            {
                return DIGEST;
            }

            @Override
            public int getMaximumProofSize()
            {
                return hashalgo.getHashSize();
            }

            @Override
            public HashAlgorithm getHashAlgorithm()
            {
                return hashalgo;
            }

            @Override
            public Set<KeyType> getRequiredKeyTypes()
            {
                return Collections.singleton( PROCESS_ID );
            }

            @Override
            public MessageDigest digester()
            {
                return hashalgo.digester();
            }

            @Override
            public boolean equals(Object obj)
            {
                if( obj==this )
                    return true;

                if( obj==null || !( obj instanceof DigestMacAlgorithm ) )
                    return false;

                DigestMacAlgorithm other = (DigestMacAlgorithm) obj;

                return other.getHashAlgorithm().equals( getHashAlgorithm() );
            }

            @Override
            public int hashCode()
            {
                return Objects.hash( DigestMacAlgorithm.class, getHashAlgorithm() );
            }
        };
    }

}
