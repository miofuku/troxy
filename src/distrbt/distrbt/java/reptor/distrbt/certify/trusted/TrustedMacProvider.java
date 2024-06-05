package reptor.distrbt.certify.trusted;

import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.CertificateProvider;

@Commutative
public class TrustedMacProvider implements CertificateProvider<TssIDHolder>
{

    protected final Trinx                   m_trinx;
    protected final SingleTrustedMacFormat  m_certformat;


    public TrustedMacProvider(Trinx trinx, SingleTrustedMacFormat certformat)
    {
        m_trinx      = Objects.requireNonNull( trinx );
        m_certformat = Objects.requireNonNull( certformat );
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( m_trinx.getImplementationName() );
    }


    @Override
    public SingleTrustedMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    public TrustedMacCertifier createMessageCertifier(short remtssid)
    {
        return new TrustedMacCertifier( m_trinx, remtssid, m_certformat );
    }


    @Override
    public TrustedMacCertifier createCertifier(TssIDHolder key)
    {
        return new TrustedMacCertifier( m_trinx, key.getTssID(), m_certformat );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(TssIDHolder key)
    {
        return new BidirectionalConnectionCertifier( createCertifier( key ) );
    }


    @Override
    public TrustedMacGroupCertifier createItoNGroupCertifier(Collection<? extends TssIDHolder> keys)
    {
        return groupCertifier( -1, keys );
    }


    @Override
    public TrustedMacGroupCertifier createNtoNGroupCertifier(int locidx, Collection<? extends TssIDHolder> keys)
    {
        Preconditions.checkArgument( locidx>=0 && locidx<keys.size() );

        return groupCertifier( locidx, keys );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, TssIDHolder key)
    {
        return createUnicastCertifier( key );
    }


    protected TrustedMacGroupCertifier groupCertifier(int locidx, Collection<? extends TssIDHolder> keys)
    {
        TrustedMacCertifier  grpcertif  = createMessageCertifier( (short) -1 );
        TrustedMacCertifier[] grpverifs = new TrustedMacCertifier[ keys.size() ];

        int i=0;
        for( TssIDHolder holder : keys )
        {
            if( i!=locidx )
                grpverifs[ i ] = createMessageCertifier( holder.getTssID() );
            i++;
        }

        return new TrustedMacGroupCertifier( grpcertif, grpverifs );
    }

}
