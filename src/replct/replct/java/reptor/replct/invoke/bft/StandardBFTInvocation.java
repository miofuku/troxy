package reptor.replct.invoke.bft;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.CompoundCertification;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.distrbt.certify.debug.WrappedCertification;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrustedAlgorithm;
import reptor.jlib.hash.HashAlgorithm;
import reptor.replct.ReplicaGroup;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.common.settings.CertificationMethodBuilder;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Handshaking;


public class StandardBFTInvocation extends BFTInvocation
{

    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> m_clitorepcert;
    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> m_reptoclicert;
    private boolean                     m_tssforclis;

    private ProofAlgorithm              m_defproofalgo;
    private HashAlgorithm               m_defpredigalgo;
    private boolean                     m_routeovercontact = false;
    private boolean                     m_verifyreplies    = true;
    private boolean                     m_dummyreqcerts    = false;


    public StandardBFTInvocation(ProofAlgorithm defproofalgo, HashAlgorithm defpredigalgo,
                                 ReplicaGroup repgroup, short nclients, int[][] clint_to_addrs, int invwndsize)
    {
        super( repgroup, nclients, clint_to_addrs, invwndsize );

        m_defproofalgo   = defproofalgo;
        m_defpredigalgo  = defpredigalgo;
    }


    @Override
    public BFTInvocation load(SettingsReader reader)
    {
        super.load( reader );

        CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> stdcert, replycert;

        CertificationMethodBuilder certbuilder = createCertificationBuilder();

        stdcert   = certbuilder.load( reader, "crypto.clients" ).create();
        replycert = certbuilder.defaultCertification( stdcert ).load( reader, "crypto.replies" ).create();

        certificationByMessages( stdcert, replycert );

        m_verifyreplies = reader.getBool( "benchmark.verify_replies", m_verifyreplies );
        m_dummyreqcerts = reader.getBool( "benchmark.dummy_request_certs", m_dummyreqcerts );

        return this;
    }


    @Override
    public void addRequiredKeyTypesTo(Set<KeyType> clitoreptypes, Set<KeyType> reptoclitypes, Set<KeyType> reptoreptypes)
    {
        clitoreptypes.addAll( m_clitorepcert.getRequiredKeyTypes() );
        reptoclitypes.addAll( m_reptoclicert.getRequiredKeyTypes() );
    }


    @Override
    public BFTInvocation activate()
    {
        super.activate();

        if( m_clitorepcert==null )
            initCertificationDefault();

        m_tssforclis = m_clitorepcert.usesProofAlgorithm( TrustedAlgorithm.class ) ||
                       m_reptoclicert.usesProofAlgorithm( TrustedAlgorithm.class );

        if( m_dummyreqcerts || !m_verifyreplies )
            m_clitorepcert = new WrappedCertification<>( m_clitorepcert, !m_dummyreqcerts, m_verifyreplies, !m_verifyreplies );

        if( m_dummyreqcerts )
            m_reptoclicert = new WrappedCertification<>( m_reptoclicert, true, true, true );

        return this;
    }


    protected CertificationMethodBuilder createCertificationBuilder()
    {
        return new CertificationMethodBuilder( m_defproofalgo, m_defpredigalgo );
    }


    public void initCertificationDefault()
    {
        CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> stdcert, replycert;

        CertificationMethodBuilder certbuilder = new CertificationMethodBuilder( m_defproofalgo, m_defpredigalgo );

        stdcert   = certbuilder.create();
        replycert = certbuilder.defaultCertification( stdcert ).create();

        certificationByMessages( stdcert, replycert );
    }


    public BFTInvocation certificationByRoles(
            CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> clitorepcert,
            CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> reptoclicert)
    {
        Preconditions.checkState( !m_isactive );

        m_clitorepcert = Objects.requireNonNull( clitorepcert );
        m_reptoclicert = Objects.requireNonNull( reptoclicert );

        return this;
    }


    public BFTInvocation certificationByMessages(
            CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> stdcert,
            CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> replycert)
    {
        Preconditions.checkState( !m_isactive );
        Objects.requireNonNull( stdcert );

        if( replycert==stdcert || replycert==null )
            m_clitorepcert = m_reptoclicert = stdcert;
        else
        {
            m_clitorepcert = new CompoundCertification<>( stdcert, replycert );
            m_reptoclicert = new CompoundCertification<>( replycert, stdcert );
        }

        return this;
    }


    @Override
    public BFTInvocationClient createClient(boolean summarize)
    {
        Preconditions.checkState( m_isactive );

        Handshaking<?> hs = m_connect.getHandshakeForClients();

        return new StandardBFTInvocationClient( this, hs, summarize );
    }


    @Override
    public BFTInvocationReplica createReplica(ReplicaPeerGroup repgroup)
    {
        Preconditions.checkState( m_isactive );

        return new StandardBFTInvocationReplica( this, repgroup );
    }


    @Override
    public AuthorityInstances createAuthorityInstancesForClientHandler(short clino)
    {
        Trinx tssinst = m_tssforclis ? m_crypto.createTssInstance( clino, (short) 0, 0 ) : null;

        return m_crypto.createAuthorityInstances( clino, tssinst );
    }


    @Override
    public AuthorityInstances createAuthorityForClientShard(byte repno, short clintshard)
    {
        Trinx tssinst;

        if( !m_tssforclis )
            tssinst = null;
        else
        {
            short tssno = (short) ( m_order.getNumberOfWorkers()+clintshard );
            tssinst = m_crypto.createTssInstance( repno, tssno, 0 );
        }

        return m_crypto.createAuthorityInstances( repno, tssinst );
    }


    public GroupConnectionCertifier createClientToReplicaCertifier(short clino, short clintshard, byte contact,
                                                                   AuthorityInstances authinsts)
    {
        Collection<ConnectionKeys> repkeys = getCryptography().getReplicaKeysForClient( clino, clintshard );

        return getClientToReplicaCertification().createCertificationProvider( authinsts )
                                                .createItoNGroupCertifier( repkeys );
    }


    @Override
    public ConnectionCertifier createReplicaToClientCertifier(ReplicaPeerGroup repgroup, short clintshard, short clino,
                                                              CertificationProvider<? super ConnectionKeys> reptoclicerts)
    {
        byte repno     = repgroup.getReplicaNumber();
        byte nreplicas = repgroup.size();

        return reptoclicerts.createNtoICertifier( repno, nreplicas, getCryptography().getConnectionKeys( repno, clintshard, clino ) );
    }


    @Override
    public boolean getRouteRepliesOverContact()
    {
        return m_routeovercontact;
    }


    @Override
    public boolean usesTrustedSubsystem()
    {
        return m_tssforclis;
    }


    public CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getClientToReplicaCertification()
    {
        return m_clitorepcert;
    }


    public CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getReplicaToClientCertification()
    {
        return m_reptoclicert;
    }

}
