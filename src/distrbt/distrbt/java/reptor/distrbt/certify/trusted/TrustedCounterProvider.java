package reptor.distrbt.certify.trusted;

import java.util.Collection;

import reptor.chronos.Commutative;

@Commutative
public class TrustedCounterProvider extends AbstractCounterProvider<CounterCertifier, TrustedCounterGroupCertifier>
{

    private final SingleTrustedMacFormat m_certformat;


    public TrustedCounterProvider(Trinx trinx, SingleTrustedMacFormat certformat, int ctrno)
    {
        super( trinx, ctrno );

        m_certformat = certformat;
    }


    @Override
    public SingleTrustedMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public CounterCertifier messageCertifier(short remtssid)
    {
        return new CounterCertifier( m_trinx, remtssid, m_ctrno, m_certformat );
    }


    @Override
    protected TrustedCounterGroupCertifier groupCertifier(CounterCertifier grpcertif,
                                                             Collection<CounterCertifier> grpverifs)
    {
        return new TrustedCounterGroupCertifier( grpcertif, grpverifs.toArray( new CounterCertifier[ grpverifs.size() ] ) );
    }

}
