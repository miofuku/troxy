package reptor.distrbt.certify.mac;

import java.util.Objects;

import javax.crypto.Mac;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class MacCertifier implements BidirectionalCertifier
{

    private final Mac             m_mac;
    private final SingleMacFormat m_certformat;
    private final boolean         m_reqdigest;
    private final int             m_certsize;


    public MacCertifier(Mac mac, SingleMacFormat certformat)
    {
        m_mac        = Objects.requireNonNull( mac );
        m_certformat = Objects.requireNonNull( certformat );
        m_reqdigest  = certformat.getDigestAlgorithm()!=null;
        m_certsize   = certformat.getCertificateSize();
    }


    @Override
    public final int getCertificateSize()
    {
        return m_certsize;
    }


    @Override
    public boolean requiresDigestedData()
    {
        return m_reqdigest;
    }


    @Override
    public HashAlgorithm getDigestAlgorithm()
    {
        return m_certformat.getDigestAlgorithm();
    }


    @Override
    public SingleMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_certformat.writeCertificateTo( out, this, createMac( data ) );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        return certdata.matches( createMac( data ), 0, m_certsize, m_certformat.getProofOffset() );
    }


    private byte[] createMac(Data msgdata)
    {
        msgdata.writeTo( m_mac );

        return m_mac.doFinal();
    }

}
