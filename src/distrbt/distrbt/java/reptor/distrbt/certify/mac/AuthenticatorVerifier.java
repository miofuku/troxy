package reptor.distrbt.certify.mac;

import reptor.distrbt.certify.Verifier;
import reptor.distrbt.common.data.Data;
import reptor.jlib.hash.HashAlgorithm;

import com.google.common.base.Preconditions;


public class AuthenticatorVerifier implements Verifier
{

    private final Verifier                   m_univerif;
    private final boolean                    m_reqdigest;
    private final UniformAuthenticatorFormat m_certformat;
    private final int                        m_certsize;
    private final int                        m_offset;


    public static AuthenticatorVerifier
            createForUniformItoNAuthenticator(Verifier univerif, int nprocs, int locno)
    {
        Preconditions.checkArgument( nprocs>0 );
        Preconditions.checkArgument( locno>=0 && locno<nprocs );

        int offset = locno*univerif.getCertificateSize();

        return new AuthenticatorVerifier( univerif, nprocs, offset );
    }


    public static AuthenticatorVerifier
            createForUniformNtoNAuthenticator(Verifier univerif, int nprocs, int locno, int remno)
    {
        Preconditions.checkArgument( nprocs>0 );
        Preconditions.checkArgument( locno>=0 && locno<nprocs );
        Preconditions.checkArgument( remno>=0 && remno<nprocs );
        Preconditions.checkArgument( locno!=remno );

        int offset = ( locno<remno ? locno : locno-1 )*univerif.getCertificateSize();

        return new AuthenticatorVerifier( univerif, nprocs-1, offset );
    }


    public AuthenticatorVerifier(Verifier verifier, int nprocs, int offset)
    {
        m_univerif   = verifier;
        m_reqdigest  = verifier.requiresDigestedData();
        m_certformat = Authenticating.authenticatorFormat( nprocs, verifier.getCertificateFormat() );
        m_certsize   = m_certformat.getCertificateSize();
        m_offset     = offset;
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
    public boolean verifyCertificate(Data data, Data certdata)
    {
        certdata.adaptSlice( m_offset );
        boolean res = m_univerif.verifyCertificate( data, certdata );
        certdata.adaptSlice( -m_offset );

        return res;
    }


    public Verifier getUnicastVerifier()
    {
        return m_univerif;
    }

}
