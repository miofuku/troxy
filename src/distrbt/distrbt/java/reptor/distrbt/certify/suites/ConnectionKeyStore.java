package reptor.distrbt.certify.suites;

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.Certificate;

public class ConnectionKeyStore implements ConnectionKeys
{

    private final int         m_procid;
    private final Key         m_sharedkey;
    private final PublicKey   m_publickey;
    private final Certificate m_cert;
    private final short       m_tssid;


    public ConnectionKeyStore(int procid, Key sharedkey, PublicKey publickey, short tssid)
    {
        this( procid, sharedkey, publickey, null, tssid );
    }

    public ConnectionKeyStore(int procid, Key sharedkey, Certificate cert, short tssid)
    {
        this( procid, sharedkey, cert!=null ? cert.getPublicKey() : null, cert, tssid );
    }

    private ConnectionKeyStore(int procid, Key sharedkey, PublicKey publickey, Certificate cert, short tssid)
    {
        m_procid    = procid;
        m_sharedkey = sharedkey;
        m_publickey = publickey;
        m_cert      = cert;
        m_tssid     = tssid;
    }

    // TODO: Remove this. Its application dependent.
    @Override
    public int getProcessID()
    {
        if( m_procid<0 )
            throw new UnsupportedOperationException();

        return m_procid;
    }

    @Override
    public Key getSharedKey()
    {
        if( m_sharedkey==null )
            throw new UnsupportedOperationException();

        return m_sharedkey;
    }

    @Override
    public PublicKey getPublicKey()
    {
        if( m_publickey==null )
            throw new UnsupportedOperationException();

        return m_publickey;
    }

    public Certificate getCertificate()
    {
        if( m_cert==null )
            throw new UnsupportedOperationException();

        return m_cert;
    }

    @Override
    public short getTssID()
    {
        if( m_tssid<0 )
            throw new UnsupportedOperationException();

        return m_tssid;
    }

}
