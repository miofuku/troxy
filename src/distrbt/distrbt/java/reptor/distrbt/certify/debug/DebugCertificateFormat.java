package reptor.distrbt.certify.debug;

import reptor.distrbt.certify.CertificateFormat;
import reptor.distrbt.certify.Certifiying;
import reptor.jlib.hash.HashAlgorithm;


public class DebugCertificateFormat implements CertificateFormat
{

    private final int           m_certsize;
    private final HashAlgorithm m_digalgo;


    public DebugCertificateFormat(int certsize, HashAlgorithm digalgo)
    {
        m_certsize = certsize;
        m_digalgo  = digalgo;
    }


    @Override
    public String toString()
    {
        return Certifiying.formatName( "DUMMY_" + Integer.toString( m_certsize ), m_digalgo==null ? null : m_digalgo.getName() );
    }


    @Override
    public HashAlgorithm getDigestAlgorithm()
    {
        return m_digalgo;
    }


    @Override
    public int getCertificateSize()
    {
        return m_certsize;
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof DebugCertificateFormat ) )
            return false;

        DebugCertificateFormat other = (DebugCertificateFormat) obj;

        return other.m_certsize==m_certsize && other.m_digalgo.equals( m_digalgo );
    }

}
