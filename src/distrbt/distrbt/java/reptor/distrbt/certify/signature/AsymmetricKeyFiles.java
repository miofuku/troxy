package reptor.distrbt.certify.signature;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import com.google.common.io.Files;


public class AsymmetricKeyFiles
{

    public static CertificateFactory createDefaultCertificateFactory() throws CertificateException
    {
        return CertificateFactory.getInstance( "X.509" );
    }

    public static void saveCertificate(Certificate cert, File file) throws IOException, CertificateEncodingException
    {
        Files.write( cert.getEncoded(), file );
    }

    public static Certificate loadCertificate(CertificateFactory certfac, File file) throws IOException, CertificateException
    {
        return certfac.generateCertificate( new ByteArrayInputStream( Files.toByteArray( file ) ) );
    }

    public static void savePublicKey(PublicKey pubkey, File file) throws IOException
    {
        Files.write( new X509EncodedKeySpec( pubkey.getEncoded() ).getEncoded(), file );
    }

    public static PublicKey loadPublicKey(KeyFactory keyfac, File file) throws IOException, InvalidKeySpecException
    {
        byte[] enckey = Files.toByteArray( file );

        return keyfac.generatePublic( new X509EncodedKeySpec( enckey ) );
    }

    public static void savePrivateKey(PrivateKey prikey, File file) throws IOException
    {
        Files.write( new PKCS8EncodedKeySpec( prikey.getEncoded() ).getEncoded(), file );
    }

    public static PrivateKey loadPrivateKey(KeyFactory keyfac, File file) throws IOException, InvalidKeySpecException
    {
        byte[] enckey = Files.toByteArray( file );;

        return keyfac.generatePrivate( new PKCS8EncodedKeySpec( enckey ) );
    }

}
