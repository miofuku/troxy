package reptor.distrbt.certify;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;


public class CompoundCertificationProvider<K> implements CertificationProvider<K>
{

    private final CertificationProvider<? super K> m_certprov;
    private final CertificationProvider<? super K> m_verfprov;

    public CompoundCertificationProvider(CertificationProvider<? super K> certprov,
                                           CertificationProvider<? super K> verfprov)
    {
        m_certprov = Objects.requireNonNull( certprov );
        m_verfprov = Objects.requireNonNull( verfprov );
    }

    @Override
    public String toString()
    {
        return Certifiying.compoundMethod( m_certprov.toString(), m_verfprov.toString() );
    }

    @Override
    public ConnectionCertifier createUnicastCertifier(K key)
    {
        Certifier certif = m_certprov.createUnicastCertifier( key ).getCertifier();
        Verifier  verif  = m_verfprov.createUnicastCertifier( key ).getVerifier();

        return new CompoundConnectionCertifier( certif, verif );
    }

    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends K> keys)
    {
        Certifier  certif = m_certprov.createItoNGroupCertifier( keys ).getCertifier();
        Verifier[] verifs = toArray( m_verfprov.createItoNGroupCertifier( keys ) );

        return new CompoundGroupCertifier<>( certif, verifs );
    }

    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends K> keys)
    {
        Certifier  certif = m_certprov.createNtoNGroupCertifier( locidx, keys ).getCertifier();
        Verifier[] verifs = toArray( m_verfprov.createNtoNGroupCertifier( locidx, keys ) );

        return new CompoundGroupCertifier<>( certif, verifs );
    }

    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, K key)
    {
        Certifier certif = m_certprov.createNtoICertifier( locidx, nprocs, key ).getCertifier();
        Verifier  verif  = m_verfprov.createNtoICertifier( locidx, nprocs, key ).getVerifier();

        return new CompoundConnectionCertifier( certif, verif );
    }

    private Verifier[] toArray(VerifierGroup group)
    {
        Verifier[] verifs = new Verifier[ group.size() ];
        Arrays.setAll( verifs, i -> group.getVerifier( i ) );

        return verifs;
    }

}
