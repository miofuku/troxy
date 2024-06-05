package reptor.replct.connect;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.signature.SignatureType;
import reptor.distrbt.certify.signature.Signing;
import reptor.distrbt.com.connect.ConnectionConfiguration;
import reptor.distrbt.com.connect.StandardConnectionConfiguration;
import reptor.distrbt.com.handshake.AdaptiveSslHandshake.SslMode;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.distrbt.io.ssl.SslConfiguration;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.secure.Cryptography;


public class Connections
{

    private int                 m_reptorep_recvbufsize = 64 * 1024 * 1024;
    private int                 m_reptorep_sendbufsize = 62 * 1024 * 1024;
    private int                 m_reptocli_recvbufsize = 16 * 1024;
    private int                 m_reptocli_sendbufsize = 16 * 1024;
    private int                 m_clitorep_recvbufsize = 64 * 1024;
    private int                 m_clitorep_sendbufsize = 64 * 1024;
    private boolean             m_alwayscopy = false;
    private boolean             m_tcpnodelay = true;
    private SignatureType       m_ssltype    = null;
    private boolean             m_sslreps    = false;
    private boolean             m_sslclis    = false;
    private Handshaking<?>      m_hscli;
    private Handshaking<?>      m_hsrep;

    private Cryptography        m_crypto;

    private boolean             m_isactive   = false;


    public Connections load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        m_reptorep_recvbufsize = reader.getInt( "networks.replica.send_buffer", m_reptorep_recvbufsize );
        m_reptorep_sendbufsize = reader.getInt( "networks.replica.recv_buffer", m_reptorep_sendbufsize );
        m_reptocli_recvbufsize = reader.getInt( "networks.replica.client_send_buffer", m_reptocli_recvbufsize );
        m_reptocli_sendbufsize = reader.getInt( "networks.replica.client_recv_buffer", m_reptocli_sendbufsize );
        m_clitorep_recvbufsize = reader.getInt( "networks.client.send_buffer", m_clitorep_recvbufsize );
        m_clitorep_sendbufsize = reader.getInt( "networks.client.recv_buffer", m_clitorep_sendbufsize );

        String sigtypename = reader.getString( "networks.ssl_algo", "none" );

        if( sigtypename.equals( "none" ) )
            m_ssltype = null;
        else
        {
            m_ssltype = Signing.tryParseSignatureType( sigtypename );

            Preconditions.checkArgument( m_ssltype!=null, sigtypename );
        }

        m_sslreps = reader.getBool( "networks.replica.ssl", m_ssltype!=null );
        m_sslclis = reader.getBool( "networks.client.ssl", m_ssltype!=null );

        Preconditions.checkState( m_ssltype!=null || !m_sslreps && !m_sslclis );

        m_hsrep = m_hscli = new StandardHandshaking();

        if( m_sslreps && m_sslclis )
        {
            m_hsrep = new SslHandshaking<>( m_hsrep, this::createSslConfiguration );
            m_hscli = new SslHandshaking<>( m_hscli, this::createSslConfiguration );
        }
        else if( m_sslreps && !m_sslclis )
        {
            m_hsrep = new AdaptiveSslHandshaking<>( m_hsrep, this::createSslConfiguration, SslMode.CONNECT );
        }
        else if( !m_sslreps && m_sslclis )
        {
            m_hsrep = new AdaptiveSslHandshaking<>( m_hsrep, this::createSslConfiguration, SslMode.ACCEPT );
            m_hscli = new SslHandshaking<>( m_hscli, this::createSslConfiguration );
        }

        return this;
    }


    public Connections cryptography(Cryptography crypto)
    {
        m_crypto = Objects.requireNonNull( crypto );

        return this;
    }


    public Connections activate()
    {
        Preconditions.checkState( !m_isactive );
        Preconditions.checkState( m_crypto!=null );

        m_isactive = true;

        return this;
    }


    public ConnectionConfiguration createReplicaConnectionConfiguration(ConnectionObserver conobserver)
    {
        return new StandardConnectionConfiguration( conobserver, m_reptorep_recvbufsize, m_reptorep_sendbufsize,
                                                    m_tcpnodelay, m_sslreps, m_alwayscopy );
    }


    public ConnectionConfiguration createReplicaToClientConnectionConfiguration(ConnectionObserver conobserver)
    {
        return new StandardConnectionConfiguration( conobserver, m_reptocli_recvbufsize, m_reptocli_sendbufsize,
                                                    m_tcpnodelay, m_sslclis, m_alwayscopy );
    }


    public ConnectionConfiguration createClientToReplicaConnectionConfiguration(ConnectionObserver conobserver)
    {
        return new StandardConnectionConfiguration( conobserver, m_clitorep_recvbufsize, m_clitorep_sendbufsize,
                                                    m_tcpnodelay, m_sslclis, m_alwayscopy );
    }


    public Handshaking<?> getHandshakeForReplicas()
    {
        return m_hsrep;
    }


    public Handshaking<?> getHandshakeForClients()
    {
        return m_hscli;
    }


    public SslConfiguration createSslConfiguration(int procno)
    {
        PrivateKey  prikey  = m_crypto.getPrivateKey( (short) procno );
        Certificate pricert = m_crypto.getCertificate( (short) procno );

        Map<String, Certificate> trusted = new HashMap<>();
        for( short i=0; i<m_crypto.getNumberOfReplicas(); i++ )
            trusted.put( "replica" + i, m_crypto.getCertificate( i ) );

        return new SslConfiguration( "proc" + procno, prikey, pricert, trusted );
    }


    public SignatureType        getSslType()                            { return m_ssltype; }
    public boolean              useSslForReplicaConnections()           { return m_sslreps; }
    public boolean              useSslForClientConnections()            { return m_sslclis; }
    public int                  getReplicaToReplicaReceiveBufferSize()  { return m_reptorep_recvbufsize; }
    public int                  getReplicaToReplicaSendBufferSize()     { return m_reptorep_sendbufsize; }
    public int                  getReplicaToClientReceiveBufferSize()   { return m_reptocli_recvbufsize; }
    public int                  getReplicaToClientSendBufferSize()      { return m_reptocli_sendbufsize; }
    public int                  getClientToReplicaReceiveBufferSize()   { return m_clitorep_recvbufsize; }
    public int                  getClientToReplicaSendBufferSize()      { return m_clitorep_sendbufsize; }
    public boolean              copyAlways()                            { return m_alwayscopy; }
    public boolean              useTcpNoDelay()                         { return m_tcpnodelay; }

}
