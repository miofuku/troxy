package reptor.distrbt.certify.debug;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertification;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.CompoundGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.CertificateProvider;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.jlib.hash.HashAlgorithm;

@Immutable
public class DebugCertification implements BidirectionalCertification<Object, Object>, CertificateProvider<Object>
{

    protected final DebugCertificateFormat  m_certformat;
    protected final ProofAlgorithm          m_proofalgo;


    public DebugCertification(ProofAlgorithm proofalgo, DebugCertificateFormat certformat)
    {
        m_proofalgo  = proofalgo;
        m_certformat = Objects.requireNonNull( certformat );
    }


    public DebugCertification(ProofAlgorithm proofalgo, int certsize, HashAlgorithm digalgo)
    {
        this( proofalgo, new DebugCertificateFormat( certsize, digalgo ) );
    }


    public DebugCertification(ProofAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        this( proofalgo, new DebugCertificateFormat( proofalgo.getMaximumProofSize(), digalgo ) );
    }


    @Override
    public DebugCertification createCertificationProvider(Object insts)
    {
        return this;
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( "debug" );
    }


    @Override
    public ProofAlgorithm getProofAlgorithm()
    {
        return m_proofalgo;
    }


    @Override
    public DebugCertificateFormat getCertificateFormat()
    {
        return m_certformat;
    }


    public DebugCertifier createMessageCertifier()
    {
        return new DebugCertifier( m_certformat );
    }


    @Override
    public BidirectionalCertifier createCertifier(Object key)
    {
        return createMessageCertifier();
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(Object key)
    {
        return new BidirectionalConnectionCertifier( createMessageCertifier() );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends Object> keys)
    {
        return groupCertifier( -1, keys );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends Object> keys)
    {
        Preconditions.checkArgument( locidx>=0 && locidx<keys.size() );

        return groupCertifier( locidx, keys );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, Object key)
    {
        return createUnicastCertifier( key );
    }


    public GroupConnectionCertifier groupCertifier(int locidx, Collection<? extends Object> keys)
    {
        BidirectionalCertifier   grpcertif = createMessageCertifier();
        BidirectionalCertifier[] grpverifs = new BidirectionalCertifier[ keys.size() ];
        Arrays.fill( grpverifs, grpcertif );

        return new CompoundGroupCertifier<>( grpcertif, grpverifs );
    }

}
