package reptor.replct.secure;

import java.io.File;
import java.util.Objects;

import reptor.distrbt.certify.signature.SignatureType;


public class CryptographyEnvironment
{

    private final File m_confdir;
    private final File m_keydir;

    public CryptographyEnvironment(File confdir)
    {
        m_confdir = Objects.requireNonNull( confdir );
        m_keydir  =  new File( m_confdir, "keys" );

    }

    public File getKeyDirectory()
    {
        return m_keydir;
    }

    public File getCertificateFile(SignatureType sigtype, int keyno)
    {
        return new File( m_keydir, getCertificateFilename( sigtype, keyno ) );
    }

    public String getCertificateFilename(SignatureType sigtype, int keyno)
    {
        return getKeyFilename( sigtype, keyno, "cert" );
    }

    public File getPublicKeyFile(SignatureType sigtype, int keyno)
    {
        return new File( m_keydir, getPublicKeyFilename( sigtype, keyno ) );
    }

    public String getPublicKeyFilename(SignatureType sigtype, int keyno)
    {
        return getKeyFilename( sigtype, keyno, "pub" );
    }

    public File getPrivateKeyFile(SignatureType sigtype, int keyno)
    {
        return new File( m_keydir, getPrivateKeyFilename( sigtype, keyno ) );
    }

    public String getPrivateKeyFilename(SignatureType sigtype, int keyno)
    {
        return getKeyFilename( sigtype, keyno, "pri" );
    }

    private String getKeyFilename(SignatureType sigtype, int keyno, String ext)
    {
        return String.format( "key_%s_%d_%05d.%s", sigtype.getBaseAlgorithm().toString().toLowerCase(), sigtype.getKeySize(), keyno, ext );
    }

}
