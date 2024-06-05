package reptor.distrbt.certify.debug;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.CertificateFormat;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class DebugCertifier implements BidirectionalCertifier
{

    private final Certifier         m_certifier;
    private final Verifier          m_verifier;
    private final boolean           m_docertify;
    private final boolean           m_doverify;
    private final boolean           m_forcevalid;
    private final CertificateFormat m_certformat;
    private final int               m_certsize;
    private final boolean           m_reqdigest;

    private byte[] m_certtempl = null;


    public DebugCertifier(BidirectionalCertifier certifier, boolean docertify, boolean doverify, boolean forcevalid)
    {
        m_certifier  = certifier;
        m_verifier   = certifier;
        m_docertify  = docertify;
        m_doverify   = doverify;
        m_forcevalid = forcevalid;
        m_certformat = certifier.getCertificateFormat();
        m_certsize   = certifier.getCertificateSize();
        m_reqdigest  = certifier.requiresDigestedData();
    }


    // Creates a valid certificate for the first certification and uses this certificate afterwards.
    public DebugCertifier(Certifier certifier)
    {
        m_certifier  = certifier;
        m_verifier   = null;
        m_docertify  = false;
        m_doverify   = false;
        m_forcevalid = true;
        m_certformat = certifier.getCertificateFormat();
        m_certsize   = certifier.getCertificateSize();
        m_reqdigest  = certifier.requiresDigestedData();
    }


    public DebugCertifier(Verifier verifier)
    {
        m_certifier  = null;
        m_verifier   = verifier;
        m_docertify  = false;
        m_doverify   = true;
        m_forcevalid = true;
        m_certformat = verifier.getCertificateFormat();
        m_certsize   = verifier.getCertificateSize();
        m_reqdigest  = verifier.requiresDigestedData();
    }


    public DebugCertifier(CertificateFormat certformat)
    {
        m_certifier  = null;
        m_verifier   = null;
        m_docertify  = false;
        m_doverify   = false;
        m_forcevalid = true;
        m_certformat = certformat;
        m_certsize   = certformat.getCertificateSize();
        m_reqdigest  = certformat.getDigestAlgorithm()!=null;
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
    public CertificateFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        if( m_docertify )
            m_certifier.createCertificate( data, out );
        else if( m_certifier!=null )
        {
            if( m_certtempl==null )
            {
                m_certtempl = new byte[ m_certsize ];
                m_certifier.createCertificate( data, new DataBuffer( m_certtempl ) );
            }
            out.readFrom( m_certtempl );
        }
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        boolean isvalid = true;

        if( m_doverify )
            isvalid = m_verifier.verifyCertificate( data, certdata ) || m_forcevalid;

        return isvalid;
    }


    @Override
    public int getCertificateSize()
    {
        return m_certsize;
    }

}
