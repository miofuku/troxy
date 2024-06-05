package reptor.replct.secure;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.signature.AsymmetricKeyFiles;
import reptor.distrbt.certify.signature.SignatureType;
import reptor.distrbt.certify.suites.AuthorityInstanceStore;
import reptor.distrbt.certify.suites.ConnectionKeyStore;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.suites.JavaAuthority;
import reptor.distrbt.certify.trusted.CASHImplementation;
import reptor.distrbt.certify.trusted.DummyTrinxImplementation;
import reptor.distrbt.certify.trusted.JavaTrinxImplementation;
import reptor.distrbt.certify.trusted.JniTrinxImplementation;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.replct.common.settings.SettingsReader;


public class Cryptography
{

    private static final String TSS_SECRET = "secret";

    private final CryptographyEnvironment   m_env;

    private final Set<KeyType>              m_clitoreptypes;
    private final Set<KeyType>              m_reptoclitypes;
    private final Set<KeyType>              m_reptoreptypes;

    private final byte                      m_nreplicas;
    private final short                     m_nclients;
    private final int                       m_ntssperreplica;
    private final int                       m_clishardoffset;

    private SignatureType                   m_sigtype = null;
    private boolean                         m_usestss = false;
    private String                          m_tsslib  = null;
    private String                          m_tssenc  = null;
    private TrinxImplementation                   m_tss     = null;

    private final SecretKeyFactory          m_keyfac;
    private final CertificateFactory        m_certfac;

    private HashMap<Short, PrivateKey>      m_prikeys = new HashMap<>();
    private HashMap<Short, Certificate>     m_certs   = new HashMap<>();
    private HashMap<KeyIndex, Key>          m_shared  = new HashMap<>();

    private boolean                         m_isactive = false;

    private static class KeyIndex
    {
        private final short m_minno;
        private final short m_maxno;

        public static KeyIndex createFor(short procno, short remno)
        {
            short minno = (short) Math.min( procno, remno );
            short maxno = (short) Math.max( procno, remno );

            return new KeyIndex( minno, maxno );
        }

        public KeyIndex(short minno, short maxno)
        {
            m_minno = minno;
            m_maxno = maxno;
        }

        @Override
        public boolean equals(Object obj)
        {
            KeyIndex other = (KeyIndex) obj;

            return other.m_minno==m_minno && other.m_maxno==m_maxno;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( m_minno, m_maxno );
        }
    }


    public Cryptography(byte nreplicas, short nclients, int ntssperreplica, int clishardoffset, CryptographyEnvironment env,
                        Set<KeyType> clitoreptypes, Set<KeyType> reptoclitypes, Set<KeyType> reptoreptypes)
    {
        m_nreplicas      = nreplicas;
        m_nclients       = nclients;
        m_ntssperreplica = ntssperreplica;
        m_clishardoffset = clishardoffset;
        m_env            = env;
        m_clitoreptypes  = Objects.requireNonNull( clitoreptypes );
        m_reptoclitypes  = Objects.requireNonNull( reptoclitypes );
        m_reptoreptypes  = Objects.requireNonNull( reptoreptypes );

        processKeyTypes( m_clitoreptypes );
        processKeyTypes( m_reptoclitypes );
        processKeyTypes( m_reptoreptypes );

        try
        {
            m_keyfac  = SecretKeyFactory.getInstance( "PBEWithMD5AndDES" );
            m_certfac = AsymmetricKeyFiles.createDefaultCertificateFactory();
        }
        catch( NoSuchAlgorithmException | CertificateException e )
        {
            throw new IllegalStateException( e );
        }
    }


    private void processKeyTypes(Set<KeyType> types)
    {
        for( KeyType type : types )
        {
            if( type.equals( TrustedCertifying.TSS_ID ) )
                m_usestss = true;
            else if( type instanceof SignatureType )
            {
                if( m_sigtype==null )
                    m_sigtype = (SignatureType) type;
                else if( !m_sigtype.equals( type ) )
                    throw new UnsupportedOperationException( m_sigtype + " vs. " + type );
            }
        }
    }


    public Cryptography load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        if( m_usestss )
            loadTss( reader );

        return this;
    }


    private void loadTss(SettingsReader reader)
    {
        String tssname = reader.getString( "crypto.replicas.trusted", "java" );

        try
        {
            switch( tssname )
            {
            case "java":
                {
                    Mac mac = Authenticating.HMAC_SHA256.macCreator();
                    mac.init( generateSharedKey( TSS_SECRET ) );
                    m_tss = new JavaTrinxImplementation( mac );
                    break;
                }
            case "dummy":
                {
                    m_tss = new DummyTrinxImplementation( TrustedCertifying.TCTR_HMAC_SHA256.getMaximumProofSize(), false );
                    break;
                }
            case "cash":
                {
                    m_tss = new CASHImplementation();
                    break;
                }
            case "trinx":
                {
                    m_tsslib = reader.getString( "crypto.trinx.library", null );
                    m_tssenc = reader.getString( "crypto.trinx.enclave", null );

                    String libname = m_tsslib==null ? null : Files.getNameWithoutExtension( m_tsslib ).substring( 3 );

                    m_tss = new JniTrinxImplementation( libname, m_tssenc, "secret" );
                    break;
                }
            default:
                throw new UnsupportedOperationException( "Unknown trusted subsystem " + tssname );
            }
        }
        catch( InvalidKeyException e )
        {
            throw new IllegalStateException( e );
        }
    }


    public Cryptography initKeysForClient(short clino)
    {
        Preconditions.checkState( !m_isactive );

        if( m_clitoreptypes.contains( Authenticating.SHARED_KEY ) )
        {
            for( short remno=0; remno<m_nreplicas; remno++ )
                initSharedKey( clino, remno );
        }

        if( m_sigtype!=null && m_clitoreptypes.contains( m_sigtype ) )
            initPublicKeys( m_sigtype, clino, (short) 0, m_nreplicas );

        return this;
    }


    public Cryptography initKeysForReplica(byte repno)
    {
        Preconditions.checkState( !m_isactive );

        if( m_reptoreptypes.contains( Authenticating.SHARED_KEY ) )
        {
            for( short remno=0; remno<m_nreplicas; remno++ )
                if( remno!=repno )
                    initSharedKey( repno, remno );
        }

        if( m_sigtype!=null && ( m_reptoreptypes.contains( m_sigtype ) || m_reptoclitypes.contains( m_sigtype ) ) )
            initPublicKeys( m_sigtype, repno, (short) 0, m_nreplicas );

        if( m_reptoclitypes.contains( Authenticating.SHARED_KEY ) )
        {
            for( short remno=m_nreplicas; remno<m_nreplicas+m_nclients; remno++ )
                initSharedKey( repno, remno );
        }

        if( m_sigtype!=null && m_reptoclitypes.contains( m_sigtype ) )
            initPublicKeys( m_sigtype, repno, m_nreplicas, m_nclients );

        return this;
    }


    private void initPublicKeys(SignatureType sigtype, short procno, short firstno, short nprocs)
    {
        try
        {
            KeyFactory keyfac = sigtype.keyFactory();

            initPrivateKey( keyfac, sigtype, procno );
            initCertificate( m_certfac, sigtype, procno );

            for( short remno=firstno; remno<firstno+nprocs; remno++ )
                initCertificate( m_certfac, sigtype, remno );
        }
        catch( IOException e )
        {
            throw new reptor.jlib.NotImplementedException( e );
        }
        catch( GeneralSecurityException e )
        {
            throw new reptor.jlib.NotImplementedException( e );
        }
    }


    private void initPrivateKey(KeyFactory keyfac, SignatureType sigtype, short procno)
                        throws InvalidKeySpecException, IOException
    {
        if( m_prikeys.containsKey( procno ) )
            return;

        File prikeyfile = m_env.getPrivateKeyFile( sigtype, procno );
        PrivateKey prikey = AsymmetricKeyFiles.loadPrivateKey( keyfac, prikeyfile );

        m_prikeys.put( procno, prikey );
    }


    private void initCertificate(CertificateFactory certfac, SignatureType sigtype, short procno)
                        throws CertificateException, IOException
    {
        if( m_certs.containsKey( procno ) )
            return;

        File certfile = m_env.getCertificateFile( sigtype, procno );
        Certificate cert = AsymmetricKeyFiles.loadCertificate( certfac, certfile );

        m_certs.put( procno, cert );
    }


    private void initSharedKey(short procno, short remno)
    {
        assert remno!=procno;

        KeyIndex idx = KeyIndex.createFor( procno, remno );

        if( m_shared.containsKey( idx ) )
            return;

        Key key = generateSharedKey( Math.max( procno, remno ) + ":" + Math.min( procno, remno ) );

        m_shared.put( idx, key );
    }


    private Key generateSharedKey(String phrase)
    {
        try
        {
            return m_keyfac.generateSecret( new PBEKeySpec( phrase.toCharArray() ) );
        }
        catch( InvalidKeySpecException e )
        {
            throw new IllegalStateException( e );
        }
    }


    public Cryptography activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public byte getNumberOfReplicas()
    {
        return m_nreplicas;
    }


    public short getNumberOfClients()
    {
        return m_nclients;
    }


    public TrinxImplementation getTss()
    {
        return m_tss;
    }


    public String getTssLibrary()
    {
        return m_tsslib;
    }


    public String getTssEnclave()
    {
        return m_tssenc;
    }


    public AuthorityInstanceStore createAuthorityInstances(short procno, Trinx tssinst)
    {
        JavaAuthority javaca = new JavaAuthority( (int) procno, m_prikeys.get( procno ) );

        return new AuthorityInstanceStore( javaca, tssinst );
    }


    public Trinx createTssInstance(short procno, short tssno, int ncounters)
    {
        try
        {
            return m_tss.createTrinx( getTssID( procno, tssno ), ncounters );
        }
        catch( IOException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    public PrivateKey getPrivateKey(short procno)
    {
        return Objects.requireNonNull( m_prikeys.get( procno ), Short.toString( procno ) );
    }


    public Certificate getCertificate(short procno)
    {
        return Objects.requireNonNull( m_certs.get( procno ), Short.toString( procno ) );
    }


    public Collection<ConnectionKeys> getReplicaKeysForClient(short clino, short shardno)
    {
        return replicaKeys( clino, (short) ( shardno+m_clishardoffset ) );
    }


    public Collection<ConnectionKeys> getReplicaKeysForReplica(byte repno, short shardno)
    {
        return replicaKeys( repno, (short) ( shardno+m_clishardoffset ) );
    }


    private Collection<ConnectionKeys> replicaKeys(short locno, short shardno)
    {
        Collection<ConnectionKeys> repkeys = new ArrayList<>( m_nreplicas );

        for( short i=0; i<m_nreplicas; i++ )
        {
            if( i==locno )
                repkeys.add( null );
            else
                repkeys.add( getConnectionKeys( locno, shardno, i ) );
        }

        return repkeys;
    }


    public ConnectionKeys getConnectionKeys(short procno, short shardno, short remno)
    {
        short tssno = procno>=m_nreplicas || remno<m_nreplicas ? shardno : 0;

        Key key = m_shared.get( KeyIndex.createFor( procno, remno ) );

        return new ConnectionKeyStore( remno, key, m_certs.get( remno ), getTssID( remno, tssno ) );
    }


    public short getTssID(short procno, short tssno)
    {
        if( m_ntssperreplica==0 )
            return -1;
        else if( procno<m_nreplicas )
        {
            // FIXME: Does not work when multiple client shards are assigned to an order shard.
//            Preconditions.checkArgument( tssno<m_ntssperreplica );
            if( tssno>=m_ntssperreplica )
                return -1;
            else
                return (short) ( procno*m_ntssperreplica + tssno );
        }
        else
        {
            Preconditions.checkArgument( tssno==0 );

            return (short) ( m_nreplicas*m_ntssperreplica + procno-m_nreplicas );
        }
    }

}
