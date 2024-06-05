package reptor.distrbt.certify.signature;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.CompoundGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;
import reptor.jlib.hash.HashAlgorithm;

@Immutable
public class JavaSignatureCertification implements SignatureCertification, SignatureProvider
{

    protected final SignatureAuthorityInstance  m_authinst;
    protected final SingleSignatureFormat       m_certformat;
    protected final PrivateKey                  m_privkey;


    public JavaSignatureCertification(SignatureAuthorityInstance authinst, SingleSignatureFormat certformat, PrivateKey privkey)
    {
        m_authinst   = authinst;
        m_certformat = Objects.requireNonNull( certformat );
        m_privkey    = Objects.requireNonNull( privkey );
    }


    public JavaSignatureCertification(SignatureAuthorityInstance authinst, SignatureAlgorithm sigalgo, HashAlgorithm digalgo,
                                      PrivateKey privkey)
    {
        this( authinst, new PlainSingleSignatureFormat( sigalgo, digalgo ), privkey );
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( m_authinst==null ? "java" : m_authinst.toString() );
    }


    @Override
    public SingleSignatureFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public SignatureAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


    @Override
    public JavaSignatureCertification createCertificationProvider(SignatureAuthorityInstance authinst)
    {
        Preconditions.checkArgument( authinst==m_authinst );

        return this;
    }


    @Override
    public Certifier createSignatureCertifier()
    {
        return certifier( m_privkey, null );
    }


    @Override
    public Verifier createSignatureVerifier(PublicKey pubkey)
    {
        return certifier( null, Objects.requireNonNull( pubkey ) );
    }


    @Override
    public BidirectionalCertifier createMessageCertifier(PublicKey pubkey)
    {
        return certifier( m_privkey, Objects.requireNonNull( pubkey ) );
    }


    protected BidirectionalCertifier certifier(PrivateKey privkey, PublicKey pubkey)
    {
        Signature sigcertif, sigverif;

        try
        {
            if( privkey==null )
                sigcertif = null;
            else
            {
                sigcertif = m_certformat.getProofAlgorithm().signer();
                sigcertif.initSign( privkey );
            }

            if( pubkey==null )
                sigverif = null;
            else
            {
                sigverif = m_certformat.getProofAlgorithm().signer();
                sigverif.initVerify( pubkey );
            }
        }
        catch( InvalidKeyException e )
        {
            throw new IllegalArgumentException( e );
        }

        return new SignatureCertifier( sigcertif, sigverif, m_certformat );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(PublicKeyHolder key)
    {
        return new BidirectionalConnectionCertifier( createCertifier( key ) );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends PublicKeyHolder> keys)
    {
        return groupCertifier( -1, keys );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends PublicKeyHolder> keys)
    {
        Preconditions.checkArgument( locidx>=0 && locidx<keys.size() );

        return groupCertifier( locidx, keys );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, PublicKeyHolder key)
    {
        return createUnicastCertifier( key );
    }


    public GroupConnectionCertifier groupCertifier(int locidx, Collection<? extends PublicKeyHolder> keys)
    {
        Certifier  grpcertif = createSignatureCertifier();
        Verifier[] grpverifs = new Verifier[ keys.size() ];

        int i=0;
        for( PublicKeyHolder holder : keys )
        {
            if( i!=locidx )
                grpverifs[ i ] = createSignatureVerifier( holder.getPublicKey() );
            i++;
        }

        return new CompoundGroupCertifier<>( grpcertif, grpverifs );
    }

}
