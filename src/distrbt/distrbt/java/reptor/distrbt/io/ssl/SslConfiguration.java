package reptor.distrbt.io.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import reptor.chronos.Immutable;
import reptor.jlib.NotImplementedException;


@Immutable
public class SslConfiguration
{

    private static final String PASSWORD = "Guess what, it's password.";

    protected String                    m_protocol      = "TLSv1.2";
    protected String                    m_prikeyalias;
    protected PrivateKey                m_prikey;
    protected Certificate               m_prikeycert;
    protected Map<String, Certificate>  m_trustedcerts;


    public SslConfiguration(String prikeyalias, PrivateKey prikey, Certificate prikeycert, Map<String, Certificate> trustedcerts)
    {
        m_prikeyalias  = Objects.requireNonNull( prikeyalias );
        m_prikey       = Objects.requireNonNull( prikey );
        m_prikeycert   = Objects.requireNonNull( prikeycert );
        m_trustedcerts = Objects.requireNonNull( trustedcerts );
    }


    public static void enableDebuggingOutput()
    {
        System.setProperty( "javax.net.debug", "ssl,handshake,all" );
    }


    public SSLContext sslContext()
    {
        try
        {
            KeyStore keys = KeyStore.getInstance( KeyStore.getDefaultType() );
            keys.load( null, null );

            keys.setKeyEntry( m_prikeyalias, m_prikey, PASSWORD.toCharArray(), new Certificate[] { m_prikeycert } );

            for( Map.Entry<String, Certificate> tcert : m_trustedcerts.entrySet() )
                keys.setCertificateEntry( tcert.getKey(), tcert.getValue() );

            KeyManagerFactory kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
            kmf.init( keys, PASSWORD.toCharArray() );

            TrustManagerFactory tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            tmf.init( keys );

            SSLContext sslcntxt = SSLContext.getInstance( m_protocol );
            sslcntxt.init( kmf.getKeyManagers(), tmf.getTrustManagers(), null );

            return sslcntxt;
        }
        catch( GeneralSecurityException | IOException e )
        {
            throw new NotImplementedException( e );
        }
    }


    public String getProtocol()
    {
        return m_protocol;
    }


    public String getPrivateKeyAlias()
    {
        return m_prikeyalias;
    }


    public PrivateKey getPrivateKey()
    {
        return m_prikey;
    }


    public Certificate getPrivateKeyCertificate()
    {
        return m_prikeycert;
    }


    public Map<String, Certificate> getTrustedCertificates()
    {
        return m_trustedcerts;
    }

}
