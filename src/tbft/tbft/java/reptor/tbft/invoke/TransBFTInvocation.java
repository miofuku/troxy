package reptor.tbft.invoke;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.jlib.hash.HashAlgorithm;
import reptor.replct.ReplicaGroup;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.SslHandshaking;
import reptor.replct.connect.StandardHandshaking;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.BFTInvocationClient;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.tbft.ClientAssigning;
import reptor.tbft.TroxyImplementation;
import reptor.tbft.adapt.TroxyHandshaking;
import reptor.tbft.adapt.TroxyHandshaking.HandshakeHandlerMapping;


public class TransBFTInvocation extends BFTInvocation
{

    public enum HandshakeType
    {
        ANNOUNCEMENT,
        ASSIGNMENT,
    }


    private final HandshakeType             m_hstype;

    private final short                     m_nhshandls;
    private final HandshakeHandlerMapping   m_handlmap;

    private TroxyImplementation             m_troxyimpl;
    private TroxyHandshaking                m_handshake;


    public TransBFTInvocation(ProofAlgorithm defproofalgo, HashAlgorithm defpredigalgo,
                              ReplicaGroup repgroup, short nclients, int[][] clint_to_addrs, int invwndsize,
                              short nhshandls, HandshakeHandlerMapping handlmap,
                              HandshakeType hstype)
    {
        super( repgroup, nclients, clint_to_addrs, invwndsize );

        m_nhshandls = nhshandls;
        m_handlmap  = Objects.requireNonNull( handlmap );
        m_hstype    = Objects.requireNonNull( hstype );
    }


    public TransBFTInvocation troxy(TroxyImplementation troxyimpl)
    {
        Preconditions.checkState( !m_isactive );

        m_troxyimpl = troxyimpl;

        return this;
    }


    public TroxyImplementation getTroxyImplementation()
    {
        return m_troxyimpl;
    }


    @Override
    public BFTInvocation activate()
    {
        super.activate();

        Preconditions.checkState( m_troxyimpl!=null );

        Handshaking<RemoteEndpoint> handshake;

        switch( m_hstype )
        {
        case ANNOUNCEMENT:
            handshake = new StandardHandshaking();
            break;
        case ASSIGNMENT:
            handshake = new ClientAssigning( m_clitowrk );
            break;
        default:
            throw new IllegalStateException();
        }

        if( m_connect.useSslForClientConnections() )
            handshake = new SslHandshaking<>( handshake, m_connect::createSslConfiguration );

        m_handshake = new TroxyHandshaking( m_handlmap, handshake );

        return this;
    }


    public short getNumberOfClients()
    {
        return m_nclients;
    }


    public short getNumberOfHandshakeHandlers()
    {
        return m_nhshandls;
    }


    @Override
    public void addRequiredKeyTypesTo(Set<KeyType> clitoreptypes, Set<KeyType> reptoclitypes, Set<KeyType> reptoreptypes)
    {
    }


    @Override
    public BFTInvocationClient createClient(boolean summarize)
    {
        Preconditions.checkState( m_isactive );

        return new TransBFTInvocationClient( this, m_handshake, summarize );
    }


    @Override
    public BFTInvocationReplica createReplica(ReplicaPeerGroup repgrp)
    {
        Preconditions.checkState( m_isactive );

        return new TransBFTInvocationReplica( this, repgrp ).troxy( m_troxyimpl.createTroxy( repgrp.getReplicaNumber() ) );
    }


    public ByteBuffer createClientHandlerOutboundBuffer()
    {
        int msgoutbufsize = Math.max( m_connect.getReplicaToClientReceiveBufferSize(), m_connect.getReplicaToClientSendBufferSize() );

        return ByteBuffer.allocateDirect( msgoutbufsize );
    }


    @Override
    public ConnectionCertifier createReplicaToClientCertifier(ReplicaPeerGroup repgrp, short clintshard, short clino,
                                                              CertificationProvider<? super ConnectionKeys> reptoclicerts)
    {
        byte repno = repgrp.getReplicaNumber();

        return reptoclicerts.createUnicastCertifier( getCryptography().getConnectionKeys( repno, clintshard, clino ) );
    }


    @Override
    public Handshaking<?> getClientHandshake()
    {
        return m_handshake.getBase();
    }


    @Override
    public TroxyHandshaking getReplicaHandshake()
    {
        return m_handshake;
    }


    @Override
    public boolean getRouteRepliesOverContact()
    {
        return true;
    }


    @Override
    public AuthorityInstances createAuthorityInstancesForClientHandler(short clino)
    {
        return null;
    }


    @Override
    public AuthorityInstances createAuthorityForClientShard(byte repno, short clintshard)
    {
        return null;
    }


    @Override
    public boolean usesTrustedSubsystem()
    {
        return false;
    }

}
