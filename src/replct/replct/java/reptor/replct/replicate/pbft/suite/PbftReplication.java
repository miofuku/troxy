package reptor.replct.replicate.pbft.suite;

import java.io.IOException;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.certify.trusted.TrustedAlgorithm;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementProtocol;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.view.ViewDependentMessage;
import reptor.replct.clients.ClientHandlingProcess;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.common.settings.CertificationMethodBuilder;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.replicate.Replication;
import reptor.replct.replicate.pbft.Pbft;
import reptor.replct.replicate.pbft.PbftCertifiers;
import reptor.replct.replicate.pbft.order.PbftOrdering;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages.PbftNewViewStable;
import reptor.replct.secure.Cryptography;


public class PbftReplication extends Replication
{

    private Checkpointing   m_checkpoint;
    private PbftOrdering    m_order;

    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys>   m_stdcert;
    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys>   m_strcert;


    public PbftReplication(boolean multiexect)
    {
        super( multiexect );
    }


    @Override
    public AgreementProtocol getAgreement()
    {
        return Pbft.INSTANCE;
    }


    @Override
    public PbftReplication load(SettingsReader reader)
    {
        super.load( reader );

        CertificationMethodBuilder certbuilder = new CertificationMethodBuilder( Authenticating.HMAC_SHA256, m_defpredigalgo );
        m_strcert = certbuilder.load( reader, "crypto.replicas.strong" ).create();
        m_stdcert = certbuilder.defaultCertification( m_strcert ).load( reader, "crypto.replicas.standard" ).create();

        int defquorumsize = Pbft.INSTANCE.getDefaultQuorum().upperQuorumSize( m_nreplicas, m_nfaults );

        m_checkpoint = new Checkpointing( m_multiexect, defquorumsize ).load( reader );
        m_order      = new PbftOrdering( m_nreplicas, m_nfaults, m_checkpoint.getCheckpointInterval() ).load( reader );

        return this;
    }


    @Override
    public PbftReplication activate()
    {
        super.activate();

        m_checkpoint.activate();
        m_order.activate();

        return this;
    }


    @Override
    public Checkpointing getCheckpointing()
    {
        return m_checkpoint;
    }


    @Override
    public PbftOrdering getOrdering()
    {
        return m_order;
    }


    @Override
    public CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getStandardCertification()
    {
        return m_stdcert;
    }


    @Override
    public CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> getStrongCertification()
    {
        return m_strcert;
    }


    @Override
    public boolean usesDedicatedCheckpointProcessors()
    {
        return false;
    }


    @Override
    public boolean usesDedicatedViewChangeProcessors()
    {
        return false;
    }


    @Override
    public ViewDependentMessage createInitialStableView(ReplicaPeerGroup repgroup)
    {
        return new PbftNewViewStable( createView( 1, repgroup ) );
    }


    @Override
    public ProtocolShardModule createOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short shardno, OrderExtensions extmanager,
            MulticastLink<? super NetworkMessage> reptrans, ClientHandlingProcess cliproc,
            Cryptography crypto, ReplicaPeerGroup repgroup, BFTInvocationReplica invrep, MessageMapper mapper)
    {
        try
        {
            TrinxImplementation tss;

            if( invrep.getInvocation().usesTrustedSubsystem() ||
                    m_strcert.usesProofAlgorithm( TrustedAlgorithm.class ) ||
                    m_stdcert.usesProofAlgorithm( TrustedAlgorithm.class ) )
                tss = crypto.getTss();
            else
                tss = null;

            // Actually only trusted MACs are needed but Trinx crashes if no counters are created.
            PbftCertifiers certs = new PbftCertifiers( m_strcert, m_stdcert, repgroup, shardno, tss, 1, mapper, crypto, invrep );

            return new PbftShard( master, shardno, this, certs, repgroup, extmanager, cliproc, reptrans, mapper );
        }
        catch( IOException e )
        {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public ProtocolShardModule createViewChangeShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, MulticastLink<? super NetworkMessage> reptrans)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProtocolShardModule createCheckpointShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, MulticastLink<? super NetworkMessage> reptrans)
    {
        throw new UnsupportedOperationException();
    }

}
