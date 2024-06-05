package reptor.distrbt.certify.signature;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.ProofType;
import reptor.jlib.hash.Hashing;


public class Signing extends Certifiying
{

    public static final ProofType SIGNATURE = proofType( "SIG", true, true, true, false );

    public static final SignatureBaseAlgorithm  DSA              = SignatureBaseAlgorithm.DSA;
    public static final SignatureType           DSA_512          = DSA.keyType( 512 );
    public static final SignatureAlgorithm      DSA_512_SHA1     = DSA_512.algorithm( Hashing.SHA1 );
    public static final SignatureAlgorithm      DSA_512_SHA256   = DSA_512.algorithm( Hashing.SHA256 );
    public static final SignatureType           DSA_1024         = DSA.keyType( 1024 );
    public static final SignatureAlgorithm      DSA_1024_SHA256  = DSA_1024.algorithm( Hashing.SHA256 );
    public static final SignatureType           DSA_2048         = DSA.keyType( 2048 );
    public static final SignatureAlgorithm      DSA_2048_SHA256  = DSA_2048.algorithm( Hashing.SHA256 );

    public static final SignatureBaseAlgorithm  RSA              = SignatureBaseAlgorithm.RSA;
    public static final SignatureType           RSA_512          = RSA.keyType( 512 );
    public static final SignatureAlgorithm      RSA_512_SHA1     = RSA_512.algorithm( Hashing.SHA1 );
    public static final SignatureAlgorithm      RSA_512_SHA256   = RSA_512.algorithm( Hashing.SHA256 );
    public static final SignatureType           RSA_1024         = RSA.keyType( 1024 );
    public static final SignatureAlgorithm      RSA_1024_SHA256  = RSA_1024.algorithm( Hashing.SHA256 );
    public static final SignatureType           RSA_2048         = RSA.keyType( 2048 );
    public static final SignatureAlgorithm      RSA_2048_SHA256  = RSA_2048.algorithm( Hashing.SHA256 );
    public static final SignatureType           RSA_3072         = RSA.keyType( 3072 );
    public static final SignatureAlgorithm      RSA_3072_SHA256  = RSA_3072.algorithm( Hashing.SHA256 );

    public static final SignatureBaseAlgorithm  ECDSA            = SignatureBaseAlgorithm.ECDSA;
    public static final SignatureType           ECDSA_112        = ECDSA.keyType( 112 );
    public static final SignatureAlgorithm      ECDSA_112_SHA1   = ECDSA_112.algorithm( Hashing.SHA1 );
    public static final SignatureAlgorithm      ECDSA_112_SHA256 = ECDSA_112.algorithm( Hashing.SHA256 );
    public static final SignatureType           ECDSA_192        = ECDSA.keyType( 192 );
    public static final SignatureAlgorithm      ECDSA_192_SHA256 = ECDSA_192.algorithm( Hashing.SHA256 ); // ~1024 RSA
    public static final SignatureType           ECDSA_256        = ECDSA.keyType( 256 );
    public static final SignatureAlgorithm      ECDSA_256_SHA256 = ECDSA_256.algorithm( Hashing.SHA256 ); // ~3072 RSA
    public static final SignatureType           ECDSA_384        = ECDSA.keyType( 384 );
    public static final SignatureAlgorithm      ECDSA_384_SHA256 = ECDSA_384.algorithm( Hashing.SHA256 );
    public static final SignatureType           ECDSA_521        = ECDSA.keyType( 521 );
    public static final SignatureAlgorithm      ECDSA_521_SHA256 = ECDSA_521.algorithm( Hashing.SHA256 );


    static
    {
        Security.addProvider( new BouncyCastleProvider() );
    }


    public static SignatureBaseAlgorithm tryParseSignatureBaseAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "DSA":
            return DSA;
        case "RSA":
            return RSA;
        case "ECDSA":
            return ECDSA;
        default:
            return null;
        }
    }


    public static SignatureType tryParseSignatureType(String s)
    {
        switch( s.toUpperCase() )
        {
        case "DSA_512":
            return DSA_512;
        case "DSA_1024":
            return DSA_1024;
        case "DSA_2048":
            return DSA_2048;

        case "RSA_512":
            return RSA_512;
        case "RSA_1024":
            return RSA_1024;
        case "RSA_2048":
            return RSA_2048;
        case "RSA_3072":
            return RSA_3072;

        case "ECDSA_112":
            return ECDSA_112;
        case "ECDSA_192_SHA256":
            return ECDSA_192;
        case "ECDSA_256":
            return ECDSA_256;
        case "ECDSA_384":
            return ECDSA_384;
        case "ECDSA_521":
            return ECDSA_521;
        default:
            return null;
        }
    }


    public static SignatureAlgorithm tryParseSignatureAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "DSA_512_SHA1":
            return DSA_512_SHA1;
        case "DSA_512_SHA256":
            return DSA_512_SHA256;
        case "DSA_1024_SHA256":
        case "SHA256WITHDSA":
            return DSA_1024_SHA256;
        case "DSA_2048_SHA256":
            return DSA_2048_SHA256;

        case "RSA_512_SHA1":
            return RSA_512_SHA1;
        case "RSA_512_SHA256":
            return RSA_512_SHA256;
        case "RSA_1024_SHA256":
        case "SHA256WITHRSA":
            return RSA_1024_SHA256;
        case "RSA_2048_SHA256":
            return RSA_2048_SHA256;
        case "RSA_3072_SHA256":
            return RSA_3072_SHA256;

        case "ECDSA_112_SHA1":
            return ECDSA_112_SHA1;
        case "ECDSA_112_SHA256":
            return ECDSA_112_SHA256;
        case "ECDSA_192_SHA256":
            return ECDSA_192_SHA256;
        case "ECDSA_256_SHA256":
        case "SHA256WITHECDSA":
            return ECDSA_256_SHA256;
        case "ECDSA_384_SHA256":
            return ECDSA_384_SHA256;
        case "ECDSA_521_SHA256":
            return ECDSA_521_SHA256;
        default:
            return null;
        }
    }

}
