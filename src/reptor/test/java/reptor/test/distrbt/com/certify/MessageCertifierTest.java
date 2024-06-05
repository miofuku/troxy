package reptor.test.distrbt.com.certify;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.CompoundCertification;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.debug.DebugCertifying;
import reptor.distrbt.certify.debug.DigestMacCertifier;
import reptor.distrbt.certify.debug.DigestMacProvider;
import reptor.distrbt.certify.debug.JavaDigestMacCertification;
import reptor.distrbt.certify.debug.PlainSingleDigestMacFormat;
import reptor.distrbt.certify.debug.SingleDigestMacFormat;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.mac.AuthenticatorCertification;
import reptor.distrbt.certify.mac.AuthenticatorProvider;
import reptor.distrbt.certify.mac.MacAuthorityInstanceHolder;
import reptor.distrbt.certify.mac.MacCertification;
import reptor.distrbt.certify.mac.PlainSingleMacFormat;
import reptor.distrbt.certify.mac.SharedKeyHolder;
import reptor.distrbt.certify.signature.PlainSingleSignatureFormat;
import reptor.distrbt.certify.signature.SignatureCertification;
import reptor.distrbt.certify.signature.SignatureProvider;
import reptor.distrbt.certify.signature.Signing;
import reptor.distrbt.certify.suites.AuthorityInstanceStore;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeyStore;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.suites.JavaAuthority;
import reptor.distrbt.certify.trusted.JavaTrinxImplementation;
import reptor.distrbt.certify.trusted.SequentialCounterCertifier;
import reptor.distrbt.certify.trusted.SingleCounterFormat;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.map.BasicMessageDigestionStrategy;
import reptor.distrbt.com.map.BasicMessageMapper;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.replicate.pbft.order.PbftOrderMessages;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftPrePrepare;


public class MessageCertifierTest
{
    public static void main(String[] args) throws Exception
    {
        testCompoundCertification();
        testMAC();
        testJavaCounter();
        testInitKey();
    }


    private static void testCompoundCertification() throws Exception
    {
        KeyPairGenerator keygen = Signing.DSA_512.keyGenerator();

        JavaAuthority ca  = new JavaAuthority( 0, keygen.generateKeyPair().getPrivate() );

        SignatureCertification sc = new PlainSingleSignatureFormat( Signing.DSA_512_SHA1, null );
        SignatureProvider      sp = sc.createCertificationProvider( ca );

        MacCertification       mc = new PlainSingleMacFormat( Authenticating.HMAC_MD5, null );

        AuthenticatorCertification<MacAuthorityInstanceHolder, SharedKeyHolder> ac =
                new AuthenticatorCertification<>( mc );
        AuthenticatorProvider<? super ConnectionKeys> ap =
                ac.createCertificationProvider( () -> ca );

        System.out.println( sp.toString() );
        System.out.println( ap.toString() );

        SecretKeyFactory keyfac = SecretKeyFactory.getInstance( "PBEWithMD5AndDES" );

        ConnectionKeys[] conkeys = new ConnectionKeys[ 2 ];
        for( int i=0; i<conkeys.length; i++ )
        {
            SecretKey sharedkey = keyfac.generateSecret( new PBEKeySpec( ("secret" + i).toCharArray() ) );
            KeyPair   keypair   = keygen.generateKeyPair();

            conkeys[ i ] = new ConnectionKeyStore( i, sharedkey, keypair.getPublic(), (short) i );
        }

        GroupConnectionCertifier certifs = sp.createItoNGroupCertifier( Arrays.asList( conkeys ) );
        System.out.println( certifs.getCertifier().getCertificateFormat() );
        System.out.println( certifs.getVerifier( 0 ).getCertificateFormat() );

        GroupConnectionCertifier certifa = ap.createItoNGroupCertifier( Arrays.asList( conkeys ) );
        System.out.println( certifa.getCertifier().getCertificateFormat() );
        System.out.println( certifa.getVerifier( 0 ).getCertificateFormat() );

        CompoundCertification<AuthorityInstances, ConnectionKeys> cc = new CompoundCertification<>( sc, ac );
        CertificationProvider<? super ConnectionKeys> cp =
                cc.createCertificationProvider( new AuthorityInstanceStore( ca ) );

        System.out.println( sc );
        System.out.println( "  " + sp );
        System.out.println( ac );
        System.out.println( "  " + ap );
        System.out.println( cc );
        System.out.println( "  " + cp );

        ConnectionCertifier certifc = cp.createUnicastCertifier( conkeys[ 0 ] );
        System.out.println( certifc.getCertifier().getCertificateFormat() );
        System.out.println( certifc.getVerifier().getCertificateFormat() );

        GroupConnectionCertifier certifd = cp.createItoNGroupCertifier( Arrays.asList( conkeys ) );
        System.out.println( certifd.getCertifier().getCertificateFormat() );
        System.out.println( certifd.getVerifier( 0 ).getCertificateFormat() );

    }


    private static void testMAC() throws Exception
    {
        HashAlgorithm msgdig = Hashing.SHA256;
        MessageMapper mapper = createMapper( msgdig );

        SingleDigestMacFormat certformat = new PlainSingleDigestMacFormat( DebugCertifying.DMAC_SHA256, msgdig );
        DigestMacProvider     provider1  = new JavaDigestMacCertification( certformat, 1 );
        DigestMacProvider     provider2  = new JavaDigestMacCertification( certformat, 2 );

        System.out.println( provider1 );

        DigestMacCertifier certifier = provider1.createMessageCertifier( (short) 2 );
        DigestMacCertifier verifier  = provider2.createMessageCertifier( (short) 1 );

        PbftPrePrepare preprep = createPrePrepare( mapper );
        mapper.certifyAndSerializeMessage( preprep, certifier );

        System.out.println( verifyMessage( preprep, mapper, verifier ) );

        PbftPrePrepare preprep2 = createPrePrepare( mapper );
        mapper.certifyAndSerializeMessage( preprep2, certifier );
        System.out.println( verifyMessage( preprep2, mapper, verifier ) );
    }


    private static void testJavaCounter() throws Exception
    {
        HashAlgorithm msgdig = Hashing.MD5;
        MessageMapper mapper = createMapper( msgdig );

        SingleCounterFormat certformat = TrustedCertifying.plainTrustedCounterFormat( TrustedCertifying.TCTR_HMAC_SHA256, msgdig );

        System.out.println( certformat );

        Trinx   tminst     = new JavaTrinxImplementation( "HmacSHA256", "secret" ).createTrinx( (short) 1, 1 );
        SequentialCounterCertifier certifier  = new SequentialCounterCertifier( tminst, (short) 2, 0, certformat );

        Trinx   tminst2  = new JavaTrinxImplementation( "HmacSHA256", "secret" ).createTrinx( (short) 2, 1 );
        SequentialCounterCertifier verifier = new SequentialCounterCertifier( tminst2, (short) 1, 0, certformat );

        PbftPrePrepare preprep = createPrePrepare( mapper );
        mapper.certifyAndSerializeMessage( preprep, certifier );
        System.out.println( verifyMessage( preprep, mapper, verifier ) && certifier.counterValue( preprep.getCertificateData() )==1 );

        PbftPrePrepare preprep2 = createPrePrepare( mapper );
        mapper.certifyAndSerializeMessage( preprep2, certifier );
        System.out.println( verifyMessage( preprep2, mapper, verifier ) && certifier.counterValue( preprep2.getCertificateData() )==2 );
    }


    private static void testInitKey() throws Exception
    {
        PBEKeySpec spec = new PBEKeySpec( "secret".toCharArray() );
        SecretKey key = SecretKeyFactory.getInstance( "PBEWithMD5AndDES" ).generateSecret( spec );

        PBEKeySpec spec2 = new PBEKeySpec( "secret2".toCharArray() );
        SecretKey key2 = SecretKeyFactory.getInstance( "PBEWithMD5AndDES" ).generateSecret( spec2 );

        Mac mac = Mac.getInstance( "HmacSHA256" );
        long cnt = 10000000;

        long s = System.nanoTime();

        for( long i = 0; i < cnt; i++ )
            mac.init( i % 2 == 0 ? key : key2 );

        long e = System.nanoTime();

        System.out.println( "Time " + (e - s) / cnt + " ns" );
    }


    private static PbftPrePrepare createPrePrepare(MessageMapper mapper)
    {
        Request req = new Request( (short) 100, 101, new ImmutableDataBuffer( 40 ), false, false );
        req.setValid();
        mapper.serializeMessage( req );
        return new PbftPrePrepare( (short) 1, 1L, 2, req );
    }


    private static MessageMapper createMapper(HashAlgorithm hashalgo)
    {
        NetworkMessageRegistry.NetworkMessageRegistryBuilder builder = new NetworkMessageRegistry.NetworkMessageRegistryBuilder();

        builder.addMessageType( PbftOrderMessages.PBFT_PREPREPARE_ID, PbftPrePrepare::new )
               .addMessageType( InvocationMessages.REQUEST_ID, Request::new );

        return new BasicMessageMapper( builder.createRegistry(),
                                         BasicMessageDigestionStrategy.Variant.DigestedMessageOverDigestedContent,
                                         hashalgo );
    }


    private static boolean verifyMessage(NetworkMessage msg, MessageMapper mapper, Verifier verifier)
    {
        try
        {
            mapper.verifyMessage( msg, verifier );
            return true;
        }
        catch( VerificationException e )
        {
            return false;
        }
    }
}
