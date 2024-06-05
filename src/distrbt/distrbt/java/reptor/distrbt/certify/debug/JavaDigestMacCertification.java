package reptor.distrbt.certify.debug;

import java.util.Collection;
import java.util.Objects;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.jlib.hash.HashAlgorithm;

@Immutable
public class JavaDigestMacCertification implements DigestMacCertification, DigestMacProvider
{

    protected final SingleDigestMacFormat m_certformat;
    protected final int                   m_procid;


    public JavaDigestMacCertification(SingleDigestMacFormat certformat, int procid)
    {
        m_certformat = Objects.requireNonNull( certformat );
        m_procid     = procid;
    }


    public JavaDigestMacCertification(DigestMacAlgorithm proofalgo, HashAlgorithm digalgo, int procid)
    {
        this( new PlainSingleDigestMacFormat( proofalgo, digalgo ), procid );
    }


    @Override
    public DigestMacAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


    @Override
    public JavaDigestMacCertification createCertificationProvider(DigestMacAuthorityInstance insts)
    {
        return this;
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( "java" );
    }


    @Override
    public SingleDigestMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public DigestMacCertifier createMessageCertifier(int remid)
    {
        return new DigestMacCertifier( m_certformat.getProofAlgorithm().digester(), m_procid, remid, m_certformat );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(ProcessIDHolder key)
    {
        return new BidirectionalConnectionCertifier( createCertifier( key ) );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends ProcessIDHolder> keys)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends ProcessIDHolder> keys)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, ProcessIDHolder key)
    {
        throw new UnsupportedOperationException();
    }

}
