package reptor.distrbt.certify;

import java.util.Objects;

import com.google.common.base.Preconditions;


public class BidirectionalGroupCertifier implements GroupConnectionCertifier, ConnectionCertifier
{

    private final BidirectionalCertifier m_certifier;
    private final int                    m_grpsize;


    public BidirectionalGroupCertifier(BidirectionalCertifier certifier, int grpsize)
    {
        Preconditions.checkArgument( grpsize>0 );

        m_certifier = Objects.requireNonNull( certifier );
        m_grpsize   = grpsize;
    }


    @Override
    public int size()
    {
        return m_grpsize;
    }


    @Override
    public Certifier getCertifier()
    {
        return m_certifier;
    }


    @Override
    public Verifier getVerifier(int index)
    {
        Preconditions.checkElementIndex( index, m_grpsize );

        return m_certifier;
    }


    @Override
    public Verifier getVerifier()
    {
        return m_certifier;
    }

}
