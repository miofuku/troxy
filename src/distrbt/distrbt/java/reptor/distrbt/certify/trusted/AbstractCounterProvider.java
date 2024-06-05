package reptor.distrbt.certify.trusted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.CertificateProvider;

@Commutative
public abstract class AbstractCounterProvider<C extends BidirectionalCertifier, G extends GroupConnectionCertifier>
                            implements CertificateProvider<TssIDHolder>
{

    protected final Trinx   m_trinx;
    protected final int     m_ctrno;


    public AbstractCounterProvider(Trinx trinx, int ctrno)
    {
        m_trinx = Objects.requireNonNull( trinx );
        m_ctrno = ctrno;
    }


    @Override
    public String toString()
    {
        return getCertificateFormat().methodName( m_trinx.getImplementationName() );
    }


    public abstract C messageCertifier(short remtssid);


    @Override
    public C createCertifier(TssIDHolder key)
    {
        return messageCertifier( key.getTssID() );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(TssIDHolder key)
    {
        return new BidirectionalConnectionCertifier( createCertifier( key ) );
    }


    @Override
    public G createItoNGroupCertifier(Collection<? extends TssIDHolder> keys)
    {
        return groupCertifier( -1, keys );
    }


    @Override
    public G createNtoNGroupCertifier(int locidx, Collection<? extends TssIDHolder> keys)
    {
        Preconditions.checkArgument( locidx>=0 && locidx<keys.size() );

        return groupCertifier( locidx, keys );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, TssIDHolder key)
    {
        return createUnicastCertifier( key );
    }


    protected G groupCertifier(int locidx, Collection<? extends TssIDHolder> keys)
    {
        C             grpcertif = messageCertifier( (short) -1 );
        Collection<C> grpverifs = new ArrayList<>( keys.size() );

        int i=0;
        for( TssIDHolder holder : keys )
        {
            if( i==locidx )
                grpverifs.add( null );
            else
                grpverifs.add( messageCertifier( holder.getTssID() ) );

            i++;
        }

        return groupCertifier( grpcertif, grpverifs );
    }


    protected abstract G groupCertifier(C grpcertif, Collection<C> grpverifs);

}
