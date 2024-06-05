package reptor.distrbt.certify.trusted;

import java.nio.ByteBuffer;

import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public class SequentialCounterCertifier extends AbstractCounterCertifier
{

    private int m_counter;


    public SequentialCounterCertifier(Trinx trinx, short remtssid, int ctrno, SingleCounterFormat certformat)
    {
        super( trinx, remtssid, certformat, trinx.getCounterCertificateSize()+Integer.BYTES );

        m_counter = 0;

        counter( ctrno );
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_trinx.executeCommand( createIndependent().tss( m_tssid ).value( ++m_counter ).message( data, out ) );
        out.byteBuffer().putInt( m_counter );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        m_trinx.executeCommand( verifyIndependent().tss( m_remtssid ).value( counterValue( certdata )  ).message( data, certdata ) );
        return isCertificateValid();
    }


    public final long counterValue(Data certdata)
    {
        if( certdata.size()!=m_certsize )
            return -1L;
        else
            return counterValue( certdata, 0 );
    }


    public int counterValue(Data certdata, int offset)
    {
        int arroff = certdata.arrayOffset() + offset + m_certsize-Integer.BYTES;

        return ByteBuffer.wrap( certdata.array(), arroff, Integer.BYTES ).getInt();
    }

}
