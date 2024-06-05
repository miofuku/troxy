package reptor.distrbt.certify.mac;

import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.CompoundConnectionCertifier;
import reptor.distrbt.certify.CompoundGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.CertificateProvider;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;


public class AuthenticatorProvider<K> implements CertificationProvider<K>
{

    public static GroupConnectionCertifier createForItoN(BidirectionalCertifier[] unicertifs)
    {
        return new CompoundGroupCertifier<>( new AuthenticatorCertifier( unicertifs ), unicertifs );
    }


    public static GroupConnectionCertifier createForNtoN(int locidx, BidirectionalCertifier[] unicertifs)
    {
        Preconditions.checkArgument( unicertifs[ locidx ]==null );

        int nprocs = unicertifs.length;

        BidirectionalCertifier[] certifs   = new BidirectionalCertifier[ nprocs-1 ];
        Verifier[]               grpverifs = new Verifier[ nprocs ];

        for(int i=0, k=0; i<nprocs; i++ )
        {
            if( i==locidx )
                continue;

            certifs[ k++ ] = unicertifs[ i ];
            grpverifs[ i ] = AuthenticatorVerifier.createForUniformNtoNAuthenticator( unicertifs[ i ], nprocs, locidx, i );
        }

        Certifier grpcertif = new AuthenticatorCertifier( certifs );

        return new CompoundGroupCertifier<>( grpcertif, grpverifs );
    }


    private final CertificateProvider<? super K> m_provider;


    public AuthenticatorProvider(CertificateProvider<? super K> provider)
    {
        m_provider = Objects.requireNonNull( provider );
    }


    @Override
    public String toString()
    {
        return Authenticating.authenticatorName( m_provider.toString() );
    }


    @Override
    public ConnectionCertifier createUnicastCertifier(K key)
    {
        return m_provider.createUnicastCertifier( key );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends K> keys)
    {
        BidirectionalCertifier[] unicertifs = new BidirectionalCertifier[ keys.size() ];

        int i = 0;

        for( K k : keys )
            unicertifs[ i++ ] = m_provider.createCertifier( k );

        return createForItoN( unicertifs );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends K> keys)
    {
        BidirectionalCertifier[] unicertifs = new BidirectionalCertifier[ keys.size() ];

        int i = 0;

        for( K k : keys )
        {
            if( i!=locidx )
                unicertifs[ i ] = m_provider.createCertifier( k );
            i++;
        }

        return createForNtoN( locidx, unicertifs );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, K key)
    {
        BidirectionalCertifier certifier = m_provider.createCertifier( key );
        Verifier verifier = AuthenticatorVerifier.createForUniformItoNAuthenticator( certifier, nprocs, locidx );

        return new CompoundConnectionCertifier( certifier, verifier );
    }

}
