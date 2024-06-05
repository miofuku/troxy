package reptor.distrbt.certify.mac;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.CertificateFormat;
import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofType;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;


public class Authenticating extends Certifiying
{

    public static final KeyType SHARED_KEY = new KeyType() {};

    public static final ProofType MAC             = proofType( "HMAC", true, true, false, false );
    public static final ProofType AUTHENTICATOR   = proofType( "AUTH", true, true, false, false );

    public static final MacAlgorithm HMAC_MD5    = hmacAlgorithm( Hashing.MD5, "HmacMD5" );
    public static final MacAlgorithm HMAC_SHA1   = hmacAlgorithm( Hashing.SHA1, "HmacSha1" );
    public static final MacAlgorithm HMAC_SHA256 = hmacAlgorithm( Hashing.SHA256, "HmacSha256" );


    public static String authenticatorName(int nprocs, CertificateFormat basefmt)
    {
        return formatName( String.format( "%dx%s", nprocs, basefmt ), null );
    }


    public static String authenticatorName(String basefmt)
    {
        return formatName( String.format( "AUTH_%s", basefmt ), null );
    }


    public static MacAlgorithm tryParseMacAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "HMAC_SHA256":
        case "HMACSHA256":
            return HMAC_SHA256;
        case "HMAC_SHA1":
        case "HMACSHA1":
            return HMAC_SHA1;
        case "HMAC_MD5":
        case "HMACMD5":
            return HMAC_MD5;
        default:
            return null;
        }
    }


    public static Key createKey(String secret)
    {
        try
        {
            SecretKeyFactory hmackeyfac = SecretKeyFactory.getInstance( "PBEWithMD5AndDES" );
            return hmackeyfac.generateSecret( new PBEKeySpec( secret.toCharArray() ) );
        }
        catch( InvalidKeySpecException | NoSuchAlgorithmException e )
        {
            throw new IllegalStateException( e );
        }
    }


    public static MacAlgorithm hmacAlgorithm(HashAlgorithm hashalgo, String macname)
    {
        return new MacAlgorithm()
        {
            @Override
            public String toString()
            {
                return getName();
            }

            @Override
            public String getName()
            {
                return algorithmName( MAC, hashalgo );
            }

            @Override
            public ProofType getProofType()
            {
                return MAC;
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
                return Collections.singleton( SHARED_KEY );
            }

            @Override
            public Mac macCreator()
            {
                if( macname==null )
                    throw new UnsupportedOperationException();

                try
                {
                    return Mac.getInstance( macname );
                }
                catch( NoSuchAlgorithmException e )
                {
                    throw new UnsupportedOperationException();
                }
            }

            @Override
            public Mac macCreator(String secret)
            {
                return macCreator( createKey( secret ) );
            }

            @Override
            public Mac macCreator(Key key)
            {
                Mac mac = macCreator();

                try
                {
                    mac.init( Objects.requireNonNull( key ) );
                }
                catch( InvalidKeyException e )
                {
                    throw new IllegalArgumentException( e );
                }

                return mac;
            }

            @Override
            public boolean equals(Object obj)
            {
                if( obj==this )
                    return true;

                if( obj==null || !( obj instanceof MacAlgorithm ) )
                    return false;

                MacAlgorithm other = (MacAlgorithm) obj;

                return other.getHashAlgorithm().equals( getHashAlgorithm() );
            }

            @Override
            public int hashCode()
            {
                return Objects.hash( MacAlgorithm.class, getHashAlgorithm() );
            }
        };
    }


    public static UniformAuthenticatorFormat authenticatorFormat(int nprocs, CertificateFormat basefmt)
    {
        Preconditions.checkArgument( nprocs>1 );

        int certsize = nprocs * basefmt.getCertificateSize();

        return new UniformAuthenticatorFormat()
        {
            @Override
            public String toString()
            {
                return authenticatorName( nprocs, basefmt );
            }

            @Override
            public HashAlgorithm getDigestAlgorithm()
            {
                return basefmt.getDigestAlgorithm();
            }

            @Override
            public int getCertificateSize()
            {
                return certsize;
            }

            @Override
            public CertificateFormat getBaseFormat()
            {
                return basefmt;
            }

            @Override
            public int getNumberOfProcesses()
            {
                return nprocs;
            }

            @Override
            public boolean equals(Object obj)
            {
                if( obj==this )
                    return true;

                if( obj==null || !( obj instanceof UniformAuthenticatorFormat) )
                    return false;

                UniformAuthenticatorFormat other = (UniformAuthenticatorFormat) obj;

                return other.getNumberOfProcesses()==nprocs && other.getBaseFormat().equals( basefmt );
            }

            @Override
            public int hashCode()
            {
                return Objects.hash( nprocs, basefmt );
            }
        };

    }

}
