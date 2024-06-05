package reptor.distrbt.certify.mac;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.CertificateFormat;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class AuthenticatorCertifier implements Certifier
{

    private final Certifier[]                m_unicertifs;
    private final boolean                    m_reqdigest;
    private final UniformAuthenticatorFormat m_certformat;
    private final int                        m_certsize;


    public <C extends Certifier> AuthenticatorCertifier(C[] unicertifs)
    {
        Preconditions.checkArgument( unicertifs.length>0 );

        CertificateFormat basefmt = unicertifs[ 0 ].getCertificateFormat();
        for( int i=1; i<unicertifs.length; i++ )
            Preconditions.checkArgument( unicertifs[ i ].getCertificateFormat().equals( basefmt ) );

        m_unicertifs = unicertifs;
        m_reqdigest  = basefmt.getDigestAlgorithm()!=null;
        m_certformat = Authenticating.authenticatorFormat( unicertifs.length, basefmt );
        m_certsize   = m_certformat.getCertificateSize();
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
    public int getCertificateSize()
    {
        return m_certsize;
    }


    @Override
    public UniformAuthenticatorFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        for( Certifier c : m_unicertifs )
            c.createCertificate( data, out );
    }

}
