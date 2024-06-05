package reptor.distrbt.certify.hash;

import java.security.MessageDigest;
import java.util.Objects;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class HashCertifier implements BidirectionalCertifier
{

    private final MessageDigest    m_digest;
    private final SingleHashFormat m_certformat;
    private final boolean          m_reqdigest;
    private final int              m_certsize;


    public HashCertifier(MessageDigest digest, SingleHashFormat certformat)
    {
        m_digest     = Objects.requireNonNull( digest );
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
    public SingleHashFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_certformat.writeCertificateTo( out, this, createDigest( data ) );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        return certdata.matches( createDigest( data ), 0, m_certsize, m_certformat.getProofOffset() );
    }


    private byte[] createDigest(Data msgdata)
    {
        msgdata.writeTo( m_digest );
        return m_digest.digest();
    }

}
