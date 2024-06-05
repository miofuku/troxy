package reptor.distrbt.certify.suites;

import reptor.distrbt.certify.debug.DigestMacAuthorityInstance;
import reptor.distrbt.certify.hash.HashAuthorityInstance;
import reptor.distrbt.certify.mac.MacAuthorityInstance;
import reptor.distrbt.certify.signature.SignatureAuthorityInstance;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrustedAuthorityInstance;


public class AuthorityInstanceStore implements AuthorityInstances
{

    private final HashAuthorityInstance         m_hasauth;
    private final DigestMacAuthorityInstance    m_digauth;
    private final MacAuthorityInstance          m_macauth;
    private final SignatureAuthorityInstance    m_sigauth;
    private final TrustedAuthorityInstance      m_tss;


    public AuthorityInstanceStore(HashAuthorityInstance hasauth,
                                  DigestMacAuthorityInstance digauth, MacAuthorityInstance macauth,
                                  SignatureAuthorityInstance sigauth, TrustedAuthorityInstance tss)
    {
        m_hasauth = hasauth;
        m_digauth = digauth;
        m_macauth = macauth;
        m_sigauth = sigauth;
        m_tss     = tss;
    }


    public AuthorityInstanceStore(JavaAuthority ja)
    {
        this( ja, ja, ja, ja, null );
    }


    public AuthorityInstanceStore(JavaAuthority ja, Trinx trinx)
    {
        this( ja, ja, ja, ja, trinx );
    }


    @Override
    public HashAuthorityInstance getHashAuthorityInstance()
    {
        return m_hasauth;
    }


    @Override
    public DigestMacAuthorityInstance getDigestMacAuthorityInstance()
    {
        return m_digauth;
    }


    @Override
    public MacAuthorityInstance getMacAuthorityInstance()
    {
        return m_macauth;
    }


    @Override
    public SignatureAuthorityInstance getSignatureAuthorityInstance()
    {
        return m_sigauth;
    }


    @Override
    public TrustedAuthorityInstance getTrustedAuthorityInstance()
    {
        return m_tss;
    }

}
