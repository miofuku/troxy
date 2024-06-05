package reptor.distrbt.certify;

import java.util.Objects;


public class BidirectionalConnectionCertifier implements ConnectionCertifier
{

    private final BidirectionalCertifier m_certifier;

    public BidirectionalConnectionCertifier(BidirectionalCertifier certifier)
    {
        m_certifier = Objects.requireNonNull( certifier );
    }

    @Override
    public BidirectionalCertifier getCertifier()
    {
        return m_certifier;
    }

    @Override
    public BidirectionalCertifier getVerifier()
    {
        return m_certifier;
    }

}
