package reptor.distrbt.certify;

import java.util.Objects;


public class CompoundConnectionCertifier implements ConnectionCertifier
{

    private final Certifier m_certifier;
    private final Verifier  m_verifier;

    public CompoundConnectionCertifier(Certifier certifier, Verifier verifier)
    {
        m_certifier = Objects.requireNonNull( certifier );
        m_verifier  = Objects.requireNonNull( verifier );
    }

    @Override
    public Certifier getCertifier()
    {
        return m_certifier;
    }

    @Override
    public Verifier getVerifier()
    {
        return m_verifier;
    }

}
