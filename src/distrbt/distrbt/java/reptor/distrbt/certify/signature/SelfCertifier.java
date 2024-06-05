package reptor.distrbt.certify.signature;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;


public class SelfCertifier
{

    private final JcaContentSignerBuilder       m_siggen;
    private final JcaX509CertificateConverter   m_certcon;


    public SelfCertifier(SignatureAlgorithm sigalgo)
    {
        m_siggen  = sigalgo.signerBuilder();
        m_certcon = new JcaX509CertificateConverter().setProvider( BouncyCastleProvider.PROVIDER_NAME );
    }


    public Certificate certify(String commonname, KeyPair keypair) throws CertificateException
    {
        return certify( commonname, keypair.getPrivate(), keypair.getPublic() );
    }


    public Certificate certify(String commonname, PrivateKey prikey, PublicKey pubkey) throws CertificateException
    {
        X500NameBuilder nb = new X500NameBuilder( BCStyle.INSTANCE );
        nb.addRDN( BCStyle.O, "distrbt" );
        nb.addRDN( BCStyle.CN, commonname );
        X500Name subject = nb.build();

        long curtime = System.currentTimeMillis();
        Date notbefore = new Date( curtime - 24 * 60 * 60 * 1000L );
        Date notafter  = new Date( curtime + 3 * 365 * 24 * 60 * 60 * 1000L );

        X509v3CertificateBuilder certgen = new JcaX509v3CertificateBuilder( subject,
                                                                            BigInteger.valueOf( 1 ),
                                                                            notbefore,
                                                                            notafter,
                                                                            subject,
                                                                            pubkey );

        try
        {
            return m_certcon.getCertificate( certgen.build( m_siggen.build( prikey ) ) );
        }
        catch( OperatorCreationException e )
        {
            throw new CertificateException( e );
        }
    }

}
