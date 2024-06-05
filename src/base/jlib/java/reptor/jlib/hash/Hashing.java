package reptor.jlib.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import com.google.common.base.Preconditions;


public class Hashing
{

    public static final HashAlgorithm MD5    = javaHashAlgorithm( "MD5", 16, "MD5" );
    public static final HashAlgorithm SHA1   = javaHashAlgorithm( "SHA1", 20, "SHA-1" );
    public static final HashAlgorithm SHA256 = javaHashAlgorithm( "SHA256", 32, "SHA-256" );


    public static HashAlgorithm tryParseHashAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "SHA-256":
        case "SHA256":
            return SHA256;
        case "SHA-1":
        case "SHA1":
            return SHA1;
        case "MD-5":
        case "MD5":
            return MD5;
        default:
            return null;
        }
    }


    public static HashAlgorithm javaHashAlgorithm(String name, int hashsize, String javaname)
    {
        Objects.requireNonNull( name );
        Preconditions.checkArgument( hashsize>0 );
        Objects.requireNonNull( javaname );

        return new AbstractHashAlgorithm()
        {
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
                try
                {
                    return MessageDigest.getInstance( javaname );
                }
                catch( NoSuchAlgorithmException e )
                {
                    throw new UnsupportedOperationException( e );
                }
            }
        };
    }

}
