package reptor.distrbt.certify.suites;

import java.util.Objects;

import reptor.distrbt.certify.CertificationMethod;


public class CertificationMethodDescription<I, K>
{

    private final CertificationMethod<I, K>   m_certmethod;
    private final boolean                     m_reqsharedkey;
    private final boolean                     m_reqpubkey;
    private final boolean                     m_reqtss;

    public CertificationMethodDescription(CertificationMethod<I, K> certmethod,
                                          boolean reqsharedkey, boolean reqpubkey, boolean reqtss)
    {
        m_certmethod   = Objects.requireNonNull( certmethod );
        m_reqsharedkey = reqsharedkey;
        m_reqpubkey    = reqpubkey;
        m_reqtss       = reqtss;
    }

    public CertificationMethod<I, K> getCertificationMethod()
    {
        return m_certmethod;
    }

    public boolean requiresSharedKey()
    {
        return m_reqsharedkey;
    }

    public boolean requiresPublicKey()
    {
        return m_reqpubkey;
    }

    public boolean requiresTrustedSubsystem()
    {
        return m_reqtss;
    }

}
