package distrbt.certify.debug;


import distrbt.certify.CertificateFormat;
import distrbt.certify.MessageVerifier;
import distrbt.common.data.Data;
import jlib.hash.HashAlgorithm;

public class WrappedVerifier implements MessageVerifier
{
    private MessageVerifier m_verifier;
    private boolean         m_doverify;
    private boolean         m_forcevalid;

    public WrappedVerifier(MessageVerifier verifier, boolean doverify, boolean forcevalid)
    {
        m_verifier   = verifier;
        m_doverify   = doverify;
        m_forcevalid = forcevalid;
    }

    @Override
    public boolean verifyCertificate(Data msgdata, Data certdata, int certoffset)
    {
        boolean result = false;
        if (m_doverify)
            result = m_verifier.verifyCertificate(msgdata, certdata, certoffset);
        return m_forcevalid || result;
    }

    @Override
    public int getCertificateSize()
    {
        return m_verifier.getCertificateSize();
    }

    @Override
    public boolean requiresDigestedMessage()
    {
        return m_verifier.requiresDigestedMessage();
    }

    @Override
    public HashAlgorithm getDigestAlgorithm()
    {
        return m_verifier.getDigestAlgorithm();
    }

    @Override
    public CertificateFormat getCertificateFormat()
    {
        return m_verifier.getCertificateFormat();
    }
}
