package reptor.replct.replicate;

import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.debug.DebugCertifying;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;
import reptor.replct.NetworkProtocolComponent;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementProtocol;
import reptor.replct.agree.View;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.Ordering;
import reptor.replct.agree.view.ViewDependentMessage;
import reptor.replct.clients.ClientHandling;
import reptor.replct.clients.ClientHandlingProcess;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.secure.Cryptography;


public abstract class Replication implements NetworkProtocolComponent
{

    // TODO: Get rid of this.
    protected final boolean         m_multiexect;

    protected HashAlgorithm         m_defpredigalgo = Hashing.SHA256;
    protected byte                  m_nfaults       = 1;
    protected byte                  m_nreplicas;
    protected boolean               m_rotate        = false;

    protected BFTInvocation         m_invoke;
    protected ClientHandling        m_clients;

    protected boolean               m_isactive = false;


    public Replication(boolean multiexect)
    {
        m_multiexect = multiexect;

        m_nreplicas = (byte) getAgreement().getDefaultQuorum().minimumProcesses( m_nfaults );
    }


    public abstract AgreementProtocol getAgreement();


    public Replication load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        String hashname = reader.getString( "crypto.message_digest", null );
        if( hashname!=null )
        {
            HashAlgorithm hashalgo = Hashing.tryParseHashAlgorithm( hashname );

            if( hashalgo==null )
                throw new IllegalArgumentException( hashname );

            if( !reader.getBool( "crypto.dummy_message_digests", false ) )
                m_defpredigalgo = hashalgo;
            else
                m_defpredigalgo = DebugCertifying.dummyHashAlgorithm( hashalgo );
        }

        m_nreplicas = reader.getByte( "agreement.replicas", m_nreplicas );

        return this;
    }


    public Replication invocation(BFTInvocation invoke)
    {
        Preconditions.checkState( !m_isactive );

        m_invoke = Objects.requireNonNull( invoke );

        return this;
    }


    public Replication clientHandling(ClientHandling clients)
    {
        Preconditions.checkState( !m_isactive );

        m_clients = Objects.requireNonNull( clients );

        return this;
    }


    public void addRequiredKeyTypesTo(Set<KeyType> clitoreptypes, Set<KeyType> reptoclitypes, Set<KeyType> reptoreptypes)
    {
        reptoreptypes.addAll( getStandardCertification().getRequiredKeyTypes() );
        reptoreptypes.addAll( getStrongCertification().getRequiredKeyTypes() );
    }


    public Replication activate()
    {
        Preconditions.checkState( !m_isactive );
        Preconditions.checkState( m_invoke!=null );
        Preconditions.checkState( m_clients!=null );

        m_isactive = true;

        return this;
    }


    public byte getNumberOfReplicas()
    {
        return m_nreplicas;
    }


    public byte getNumberOfTolerableFaults()
    {
        return m_nfaults;
    }


    public HashAlgorithm getDefaultMessageDigest()
    {
        return m_defpredigalgo;
    }


    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        getCheckpointing().registerMessages( msgreg );
        getOrdering().registerMessages( msgreg );
    }


    public abstract CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getStandardCertification();


    public abstract CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getStrongCertification();


    public abstract Checkpointing getCheckpointing();


    public abstract Ordering getOrdering();


    public byte getCoordinator(int viewno)
    {
        return (byte) ( viewno % m_nreplicas );
    }


    public View createView(int viewno, ReplicaPeerGroup repgroup)
    {
        return new StaticView( viewno, this, repgroup );
    }


    public abstract ViewDependentMessage createInitialStableView(ReplicaPeerGroup repgroup);


    public abstract boolean usesDedicatedCheckpointProcessors();


    public abstract boolean usesDedicatedViewChangeProcessors();


    public abstract ProtocolShardModule
            createOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master, short no,
                    OrderExtensions extmanager, MulticastLink<? super NetworkMessage> reptrans,
                    ClientHandlingProcess cliproc, Cryptography crypto, ReplicaPeerGroup repgroup, BFTInvocationReplica invrep, MessageMapper mapper);

    public abstract ProtocolShardModule
            createViewChangeShardProcessor(SchedulerContext<? extends SelectorDomainContext> master, short no,
                    MulticastLink<? super NetworkMessage> reptrans);

    public abstract ProtocolShardModule createCheckpointShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, MulticastLink<? super NetworkMessage> reptrans);

}
