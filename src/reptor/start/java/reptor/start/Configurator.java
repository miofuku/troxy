package reptor.start;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;

import reptor.distrbt.certify.signature.AsymmetricKeyFiles;
import reptor.distrbt.certify.signature.SelfCertifier;
import reptor.distrbt.certify.signature.SignatureBaseAlgorithm;
import reptor.distrbt.certify.signature.SignatureType;
import reptor.distrbt.certify.signature.Signing;
import reptor.jlib.hash.Hashing;
import reptor.replct.secure.CryptographyEnvironment;


public class Configurator
{

    public static String getUsage()
    {
        return "Configurator <confdir> <sigbase> <keysize> <firstkeyno> <nkeys>";
    }

    public static void main(String[] args) throws Exception
    {
        if( args.length!=5 )
        {
            System.err.println( getUsage() );
            System.exit( -1 );
        }

        File    confdir;
        String  sigbasename;
        int     keysize;
        int     firstkeyno;
        int     nkeys;

        try
        {
            confdir     = new File( args[ 0 ] );
            sigbasename = args[ 1 ];
            keysize     = Integer.parseInt( args[ 2 ] );
            firstkeyno  = Integer.parseInt( args[ 3 ] );
            nkeys       = Integer.parseInt( args[ 4 ] );
        }
        catch( Exception e )
        {
            System.err.println( getUsage() );
            throw new IllegalArgumentException( e );
        }

        SignatureBaseAlgorithm sigbase   = Signing.tryParseSignatureBaseAlgorithm( sigbasename );
        SignatureType          sigtype   = sigbase.keyType( keysize );
        KeyPairGenerator       keygen    = sigtype.keyGenerator();
        SelfCertifier          certifier = new SelfCertifier( sigtype.algorithm( Hashing.SHA256 ) );

        CryptographyEnvironment env = new CryptographyEnvironment( confdir );
        env.getKeyDirectory().mkdirs();

        System.out.println( String.format( "Generate %s %d keys at %s", sigtype.getBaseAlgorithm(), sigtype.getKeySize(), env.getKeyDirectory() ) );

        for( int keyno=firstkeyno; keyno<firstkeyno+nkeys; keyno++ )
        {
            System.out.println( "  Generate key " + keyno );
            KeyPair     keypair = keygen.generateKeyPair();
            Certificate cert    = certifier.certify( "key" + keyno, keypair );

            File certfile = env.getCertificateFile( sigtype, keyno );
            AsymmetricKeyFiles.saveCertificate( cert, certfile );
            File prikeyfile = env.getPrivateKeyFile( sigtype, keyno );
            AsymmetricKeyFiles.savePrivateKey( keypair.getPrivate(), prikeyfile );
        }
    }

}
