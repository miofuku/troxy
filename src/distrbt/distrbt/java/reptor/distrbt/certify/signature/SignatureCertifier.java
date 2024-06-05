package reptor.distrbt.certify.signature;

import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class SignatureCertifier implements BidirectionalCertifier
{

    private final Signature             m_sigcertif;
    private final Signature             m_sigverif;
    private final SingleSignatureFormat m_certformat;
    private final boolean               m_reqdigest;
    private final int                   m_certsize;


    public SignatureCertifier(Signature sigcertif, Signature sigverif, SingleSignatureFormat certformat)
    {
        Preconditions.checkArgument( sigcertif!=null || sigverif!=null );

        m_sigcertif    = sigcertif;
        m_sigverif   = sigverif;
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
    public SingleSignatureFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        data.writeTo( m_sigcertif );

        try
        {
            m_certformat.writeCertificateTo( out, this, m_sigcertif.sign() );
        }
        catch( SignatureException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        data.writeTo( m_sigverif );

        try
        {
            return m_sigverif.verify( certdata.array(), certdata.arrayOffset()+m_certformat.getProofOffset(), m_certsize );
        }
        catch( SignatureException e )
        {
            return false;
        }
    }

}
