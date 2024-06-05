package reptor.distrbt.certify.suites;

import java.security.PrivateKey;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.CertificationAuthority;
import reptor.distrbt.certify.debug.DigestMacAuthorityInstance;
import reptor.distrbt.certify.debug.JavaDigestMacCertification;
import reptor.distrbt.certify.debug.SingleDigestMacFormat;
import reptor.distrbt.certify.hash.HashAuthorityInstance;
import reptor.distrbt.certify.hash.HashProvider;
import reptor.distrbt.certify.hash.JavaHashCertification;
import reptor.distrbt.certify.hash.SingleHashFormat;
import reptor.distrbt.certify.mac.JavaMacCertification;
import reptor.distrbt.certify.mac.MacAuthorityInstance;
import reptor.distrbt.certify.mac.SingleMacFormat;
import reptor.distrbt.certify.signature.JavaSignatureCertification;
import reptor.distrbt.certify.signature.SignatureAuthorityInstance;
import reptor.distrbt.certify.signature.SingleSignatureFormat;
import reptor.jlib.entities.Named;

@Immutable
public class JavaAuthority implements Named, CertificationAuthority, HashAuthorityInstance,
                                      DigestMacAuthorityInstance, MacAuthorityInstance, SignatureAuthorityInstance
{

    private final Integer    m_procid;
    private final PrivateKey m_privkey;


    public JavaAuthority()
    {
        this( null, null );
    }


    public JavaAuthority(Integer procid, PrivateKey privkey)
    {
        m_procid  = procid;
        m_privkey = privkey;
    }


    @Override
    public String getName()
    {
        return "java";
    }


    @Override
    public HashProvider createHashProvider(SingleHashFormat certformat)
    {
        return new JavaHashCertification( certformat );
    }


    @Override
    public JavaMacCertification createMacProvider(SingleMacFormat certformat)
    {
        return new JavaMacCertification( this, certformat );
    }


    @Override
    public JavaSignatureCertification createSignatureProvider(SingleSignatureFormat certformat)
    {
        if( m_privkey==null )
            throw new UnsupportedOperationException();

        return new JavaSignatureCertification( this, certformat, m_privkey );
    }


    @Override
    public JavaDigestMacCertification createDigestMacProvider(SingleDigestMacFormat certformat)
    {
        if( m_procid==null )
            throw new UnsupportedOperationException();

        return new JavaDigestMacCertification( certformat, m_procid );
    }

}
