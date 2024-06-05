package reptor.distrbt.certify.debug;

import java.security.MessageDigest;
import java.util.Objects;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class DigestMacCertifier implements BidirectionalCertifier
{

    private final MessageDigest         m_digest;
    private final int                   m_locid;
    private final int                   m_remid;
    private final SingleDigestMacFormat m_certformat;
    private final boolean               m_reqdigest;
    private final int                   m_certsize;


    public DigestMacCertifier(MessageDigest digest, int locid, int remid, SingleDigestMacFormat certformat)
    {
        m_digest     = Objects.requireNonNull( digest );
        m_locid      = locid;
        m_remid      = remid;
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
    public SingleDigestMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_certformat.writeCertificateTo( out, this, createMac( data, m_locid, m_remid ) );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        return certdata.matches( createMac( data, m_remid, m_locid ), 0, m_certsize, m_certformat.getProofOffset() );
    }


    private byte[] createMac(Data msgdata, int sender, int recipient)
    {
        msgdata.writeTo( m_digest );
        updateInt( sender );
        updateInt( recipient );
        return m_digest.digest();
    }


    private void updateInt(int value)
    {
        m_digest.update( (byte) ( value ) );
        m_digest.update( (byte) ( value>>8 ) );
        m_digest.update( (byte) ( value>>16 ) );
        m_digest.update( (byte) ( value>>24 ) );
    }

}
