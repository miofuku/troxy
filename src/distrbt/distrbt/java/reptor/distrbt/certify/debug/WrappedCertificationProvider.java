package reptor.distrbt.certify.debug;

import java.util.Collection;
import java.util.Objects;

import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.CompoundConnectionCertifier;
import reptor.distrbt.certify.CompoundGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;


public class WrappedCertificationProvider<K> implements CertificationProvider<K>
{

    private final CertificationProvider<K> m_provider;
    private final boolean                  m_docertify;
    private final boolean                  m_doverify;
    private final boolean                  m_forcevalid;


    public WrappedCertificationProvider(CertificationProvider<K> provider,
                                        boolean docertify, boolean doverify, boolean forcevalid)
    {
        m_provider   = Objects.requireNonNull( provider );
        m_docertify  = docertify;
        m_doverify   = doverify;
        m_forcevalid = forcevalid;
    }


    @Override
    public ConnectionCertifier createUnicastCertifier(K key)
    {
        ConnectionCertifier certifier = m_provider.createUnicastCertifier( key );

        return new CompoundConnectionCertifier( wrapCertifier( certifier.getCertifier() ), wrapVerifier( certifier.getVerifier() ) );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends K> keys)
    {
        return wrapGroup( m_provider.createItoNGroupCertifier( keys ) );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends K> keys)
    {
        return wrapGroup( m_provider.createNtoNGroupCertifier( locidx, keys ) );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, K key)
    {
        ConnectionCertifier certifier = m_provider.createNtoICertifier( locidx, nprocs, key );

        return new CompoundConnectionCertifier( wrapCertifier( certifier.getCertifier() ), wrapVerifier( certifier.getVerifier() ) );
    }


    private GroupConnectionCertifier wrapGroup(GroupConnectionCertifier grpcertif)
    {
        Certifier wrapcertif = wrapCertifier( grpcertif.getCertifier() );

        Verifier[] wrapverifs = new Verifier[ grpcertif.size() ];
        for( int i=0; i<grpcertif.size(); i++ )
        {
            Verifier verifier = grpcertif.getVerifier( i );

            if( verifier==null )
                continue;
            else
                wrapverifs[ i ] = wrapVerifier( verifier );
        }

        return new CompoundGroupCertifier<>( wrapcertif, wrapverifs );
    }


    private Certifier wrapCertifier(Certifier certifier)
    {
        return m_docertify ? certifier : new DebugCertifier( certifier );
    }


    private Verifier wrapVerifier(Verifier verifier)
    {
        if( !m_doverify )
            return new DebugCertifier( verifier.getCertificateFormat() );
        else if( m_forcevalid )
            return new DebugCertifier( verifier );
        else
            return verifier;
    }

}
