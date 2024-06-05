package reptor.distrbt.certify.trusted;

import java.util.Collection;

import reptor.chronos.Commutative;

@Commutative
public class SequentialCounterProvider extends AbstractCounterProvider<SequentialCounterCertifier, SequentialCounterGroupCertifier>
{

    private final SingleCounterFormat m_certformat;


    public SequentialCounterProvider(Trinx trinx, SingleCounterFormat certformat, int ctrno)
    {
        super( trinx, ctrno );

        m_certformat = certformat;
    }


    @Override
    public SingleCounterFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public SequentialCounterCertifier messageCertifier(short remtssid)
    {
        return new SequentialCounterCertifier( m_trinx, remtssid, m_ctrno, m_certformat );
    }


    @Override
    protected SequentialCounterGroupCertifier groupCertifier(SequentialCounterCertifier grpcertif,
                                                             Collection<SequentialCounterCertifier> grpverifs)
    {
        return new SequentialCounterGroupCertifier( grpcertif, grpverifs.toArray( new SequentialCounterCertifier[ grpverifs.size() ] ) );
    }

}
