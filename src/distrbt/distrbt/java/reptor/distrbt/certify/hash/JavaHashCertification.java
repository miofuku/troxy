package reptor.distrbt.certify.hash;

import java.util.Collection;
import java.util.Objects;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.BidirectionalGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.jlib.hash.HashAlgorithm;

@Immutable
public class JavaHashCertification implements HashCertification, HashProvider
{

    protected final SingleHashFormat m_certformat;


    public JavaHashCertification(SingleHashFormat certformat)
    {
        m_certformat = Objects.requireNonNull( certformat );
    }


    public JavaHashCertification(HashProofAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        this( new PlainSingleHashFormat( proofalgo, digalgo ) );
    }


    @Override
    public HashProofAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


    @Override
    public JavaHashCertification createCertificationProvider(HashAuthorityInstance insts)
    {
        return this;
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( "java" );
    }


    @Override
    public SingleHashFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public HashCertifier createMessageCertifier()
    {
        return new HashCertifier( m_certformat.getProofAlgorithm().digester(), m_certformat );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(Object key)
    {
        return createConnectionCertifier();
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<?> keys)
    {
        return new BidirectionalGroupCertifier( createMessageCertifier(), keys.size() );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<?> keys)
    {
        return new BidirectionalGroupCertifier( createMessageCertifier(), keys.size() );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, Object key)
    {
        return new BidirectionalGroupCertifier( createMessageCertifier(), nprocs );
    }


    private BidirectionalConnectionCertifier createConnectionCertifier()
    {
        return new BidirectionalConnectionCertifier( createMessageCertifier() );
    }

}
