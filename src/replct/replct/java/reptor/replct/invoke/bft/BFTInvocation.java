package reptor.replct.invoke.bft;

import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.ReplicaGroup;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.Ordering;
import reptor.replct.common.WorkDistribution;
import reptor.replct.common.quorums.QuorumDefinition;
import reptor.replct.common.quorums.QuorumDefinitions;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Connections;
import reptor.replct.connect.Handshaking;
import reptor.replct.invoke.ClientToWorkerAssignment;
import reptor.replct.invoke.Invocation;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.ReplyModeStrategies;
import reptor.replct.invoke.ReplyModeStrategies.AllButLeaderReply;
import reptor.replct.invoke.ReplyModeStrategies.AllButXReply;
import reptor.replct.invoke.ReplyModeStrategies.AllReply;
import reptor.replct.invoke.ReplyModeStrategy;
import reptor.replct.map.Mapping;
import reptor.replct.secure.Cryptography;


public abstract class BFTInvocation implements Invocation
{

    public static final long NO_INVOCATION    = 0L;
    public static final long FIRST_INVOCATION = 1L;

    protected final ReplicaGroup        m_repgroup;

    protected boolean                   m_readonlyopt      = false;
    protected ReplyModeStrategy         m_replystrat;
    protected QuorumDefinition          m_replyquorumdef;
    protected int                       m_invwndsize;
    protected byte                      m_nrepliers;
    protected int                       m_replyhash_threshold = ReplyModeStrategies.NO_REPLY_HASHING;
    protected Boolean                   m_distcontacts        = null;
    protected ClientToWorkerAssignment  m_clitowrk;

    protected Cryptography              m_crypto;
    protected Mapping                   m_map;
    protected Connections               m_connect;
    protected Ordering                  m_order;

    protected boolean                   m_isactive         = false;

    protected short                     m_nclients;
    private int[][]                     m_clint_to_addrs;

    private boolean                     isProphecy = false;

    private boolean                     useCache = false;


    public BFTInvocation(ReplicaGroup repgroup, short nclients, int[][] clint_to_addrs, int invwndsize)
    {
        m_repgroup       = Objects.requireNonNull( repgroup );
        m_nclients       = nclients;
        m_clint_to_addrs = Objects.requireNonNull( clint_to_addrs );
        m_nrepliers      = repgroup.size();
        m_invwndsize     = invwndsize;
    }


    public BFTInvocation load(SettingsReader reader)
    {
        // Options
        m_readonlyopt = reader.getBool( "client.readonly_optimization", m_readonlyopt );

        if( !reader.isDefault( "client.dist_contacts" ) )
            m_distcontacts = reader.getBool( "client.dist_contacts", false );

        // Reply mode strategy
        int replyhash;

        if( "none".equals( reader.getString( "client.replyhash_threshold", "none" ) ) )
            replyhash = ReplyModeStrategies.NO_REPLY_HASHING;
        else
            replyhash = reader.getInt( "client.replyhash_threshold", 0 );

        if( "woleader".equals( reader.getString( "client.repliers", null ) ) )
            leaderDoesNotReply( replyhash );
        else
            numberOfRepliers( reader.getByte( "client.repliers", m_repgroup.size() ), replyhash );

        isProphecy = reader.getBool( "benchmark.zero.prophecy", false );

        useCache = reader.getBool("benchmark.troxy.useCache", false);

        return this;
    }


    public BFTInvocation leaderDoesNotReply(int replyhash_threshold)
    {
        Preconditions.checkState( !m_isactive );

        m_nrepliers = -1;
        m_replyhash_threshold = replyhash_threshold;

        return this;
    }


    public BFTInvocation numberOfRepliers(byte nrepliers, int replyhash_threshold)
    {
        Preconditions.checkState( !m_isactive );
        Preconditions.checkArgument( nrepliers>=0 && nrepliers<=m_repgroup.size() );

        m_nrepliers = nrepliers;
        m_replyhash_threshold = replyhash_threshold;

        return this;
    }


    public BFTInvocation cryptography(Cryptography crypto)
    {
        m_crypto = Objects.requireNonNull( crypto );

        return this;
    }


    public BFTInvocation mapping(Mapping map)
    {
        m_map = Objects.requireNonNull( map );

        return this;
    }


    public BFTInvocation connections(Connections connect)
    {
        m_connect = Objects.requireNonNull( connect );

        return this;
    }


    public BFTInvocation ordering(Ordering order)
    {
        m_order = Objects.requireNonNull( order );

        return this;
    }


    public abstract void addRequiredKeyTypesTo(Set<KeyType> clitoreptypes, Set<KeyType> reptoclitypes, Set<KeyType> reptoreptypes);


    public BFTInvocation activate()
    {
        Preconditions.checkState( !m_isactive );
        Preconditions.checkState( m_crypto!=null );
        Preconditions.checkState( m_map!=null );
        Preconditions.checkState( m_connect!=null );
        Preconditions.checkState( m_order!=null );
        Preconditions.checkState( !( m_distcontacts==Boolean.FALSE && m_order.getUseRotatingLeader() ) );

        if( m_distcontacts==null )
            m_distcontacts = m_order.getUseRotatingLeader();

        m_clitowrk = new ClientToWorkerAssignment( m_repgroup.size(), m_nclients, m_clint_to_addrs, m_distcontacts );

        if( m_replystrat==null )
        {
            if( m_nrepliers==m_repgroup.size() )
                m_replystrat = new AllReply( m_replyhash_threshold );
            else if( m_nrepliers>=0 )
            {
                WorkDistribution noreplydist = new WorkDistribution.Blockwise( m_repgroup.size(), m_order.getNumberOfWorkers() );

                m_replystrat = new AllButXReply( m_replyhash_threshold, noreplydist, (short) ( m_repgroup.size()-m_nrepliers ) );
            }
            else
            {
                WorkDistribution fulldist = m_order.getUseRotatingLeader() ?
                        new WorkDistribution.Continuous( 0 ) :
                        new WorkDistribution.Blockwise( m_repgroup.size()-1, m_order.getNumberOfWorkers() );

                m_replystrat = new AllButLeaderReply( m_replyhash_threshold, fulldist );
            }
        }

        m_replyquorumdef = m_readonlyopt ? QuorumDefinitions.CORRECT_INTERSECTION : QuorumDefinitions.CORRECT_MEMBER;

        m_isactive = true;

        return this;
    }


    public abstract BFTInvocationClient createClient(boolean summarize);


    public abstract BFTInvocationReplica createReplica(ReplicaPeerGroup repgroup);


    public abstract AuthorityInstances createAuthorityInstancesForClientHandler(short clino);


    public abstract AuthorityInstances createAuthorityForClientShard(byte repno, short clintshard);


    public abstract ConnectionCertifier createReplicaToClientCertifier(ReplicaPeerGroup repgroup, short clintshard, short clino,
                                                                       CertificationProvider<? super ConnectionKeys> reptoclicerts);



    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        msgreg.addMessageType( InvocationMessages.REQUEST_ID, Request::new )
              .addMessageType( InvocationMessages.REPLY_ID, Reply::new );

    }


    @Override
    public Handshaking<?> getReplicaHandshake()
    {
        return m_connect.getHandshakeForReplicas();
    }


    @Override
    public Handshaking<?> getClientHandshake()
    {
        return m_connect.getHandshakeForClients();
    }


    public Cryptography getCryptography()
    {
        return m_crypto;
    }


    public Mapping getMapping()
    {
        return m_map;
    }


    public Connections getConnections()
    {
        return m_connect;
    }


    public Ordering getOrdering()
    {
        return m_order;
    }


    public ReplicaGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    @Override
    public ClientToWorkerAssignment getClientToWorkerAssignment()
    {
        return m_clitowrk;
    }


    @Override
    public int getInvocationWindowSize()
    {
        return m_invwndsize;
    }


    public ReplyModeStrategy getReplyModeStrategy()
    {
        return m_replystrat;
    }


    public Boolean getUseDistributedContacts()
    {
        return m_distcontacts;
    }


    public abstract boolean usesTrustedSubsystem();


    public abstract boolean getRouteRepliesOverContact();


    public boolean getUseReadOnlyOptimization()
    {
        return m_readonlyopt;
    }


    public short maximumNumberOfResults(short nreplicas, short nfaults)
    {
        // With speculative execution like read-only optimization there can be up to n different d results,
        // otherwise only f+1.
        return (short) ( m_readonlyopt ? nreplicas : nfaults+1 );
    }


    public short writeReplyQuorumSize(short nreplicas, short nfaults)
    {
        return (short) m_replyquorumdef.lowerQuorumSize( nreplicas, nfaults );
    }


    public short readReplyQuorumSize(short nreplicas, short nfaults)
    {
        return (short) m_replyquorumdef.upperQuorumSize( nreplicas, nfaults );
    }

    public boolean isProphecy()
    {
        return isProphecy;
    }

    public boolean isUseCache() { return useCache; }

}
