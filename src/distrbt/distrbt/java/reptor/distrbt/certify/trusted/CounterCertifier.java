package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBuffer;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.MutableData;


public class CounterCertifier extends AbstractCounterCertifier
{

    public CounterCertifier(Trinx trinx, short remtssid, int ctrno, SingleTrustedMacFormat certformat)
    {
        super( trinx, remtssid, certformat );

        counter( ctrno );
    }


    public TrustedCounterValue counterValue()
    {
        return m_trinx.counterValue( m_ctrno );
    }


    @Override
    public TrinxCommandBuffer createTrustedGroup()
    {
        return super.createTrustedGroup().tss( m_tssid );
    }

    @Override
    public TrinxCommandBuffer verifyTrustedGroup()
    {
        return super.verifyTrustedGroup().tss( m_remtssid );
    }


    @Override
    public TrinxCommandBuffer createIndependent()
    {
        return super.createIndependent().tss( m_tssid );
    }


    @Override
    public TrinxCommandBuffer verifyIndependent()
    {
        return super.verifyIndependent().tss( m_remtssid );
    }


    @Override
    public TrinxCommandBuffer createContinuing()
    {
        return super.createContinuing().tss( m_tssid );
    }


    @Override
    public TrinxCommandBuffer verifyContinuing()
    {
        return super.verifyContinuing().tss( m_remtssid );
    }


    // TODO: Should be part of the TSS interface.
    public void forwardCounterValue(long highval, long lowval)
    {
        createIndependent().value( highval, lowval );

        createCertificate( ImmutableData.EMPTY, new DataBuffer( getCertificateSize() ) );
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_trinx.executeCommand( message( data, out ) );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        m_trinx.executeCommand( message( data, certdata ) );

        return isCertificateValid();
    }

}
