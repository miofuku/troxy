package reptor.replct.replicate.hybster.suite;

import java.io.IOException;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.distrbt.certify.trusted.TrustedMacCertification;
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
import reptor.replct.replicate.hybster.Hybster;
import reptor.replct.replicate.hybster.HybsterCertifiers;
import reptor.replct.replicate.hybster.order.HybsterOrdering;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewViewStable;
import reptor.replct.secure.Cryptography;


public class HybsterReplication extends Replication
{

    private Checkpointing       m_checkpoint;
    private HybsterOrdering     m_order;

    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys>   m_stdcert;
    private TrustedMacCertification                                                   m_strcert;


    public HybsterReplication(boolean multiexect)
    {
        super( multiexect );
    }


    @Override
    public AgreementProtocol getAgreement()
    {
        return Hybster.INSTANCE;
    }


    @Override
    public HybsterReplication load(SettingsReader reader)
    {
        super.load( reader );

        CertificationMethodBuilder certbuilder = new CertificationMethodBuilder( TrustedCertifying.TMAC_HMAC_SHA256, m_defpredigalgo );
        CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> strcert;
        strcert = certbuilder.load( reader, "crypto.replicas.strong" ).create();

        if( !( strcert instanceof TrustedMacCertification ) )
            throw new IllegalArgumentException( strcert.toString() );

        m_strcert = (TrustedMacCertification) strcert;
        m_stdcert = certbuilder.defaultCertification( m_strcert ).load( reader, "crypto.replicas.standard" ).create();

        int defquorumsize = Hybster.INSTANCE.getDefaultQuorum().upperQuorumSize( m_nreplicas, m_nfaults );

        m_checkpoint = new Checkpointing( m_multiexect, defquorumsize ).load( reader );
        m_order      = new HybsterOrdering( m_nreplicas, m_nfaults, m_checkpoint.getCheckpointInterval() ).load( reader );

        return this;
    }


    @Override
    public HybsterReplication activate()
    {
        super.activate();

        m_checkpoint.activate();
        m_order.activate();

        return this;
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
    public Checkpointing getCheckpointing()
    {
        return m_checkpoint;
    }


    @Override
    public HybsterOrdering getOrdering()
    {
        return m_order;
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
        return new HybsterNewViewStable( createView( 1, repgroup ), null );
    }


    @Override
    public ProtocolShardModule createOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short shardno, OrderExtensions extmanager,
            MulticastLink<? super NetworkMessage> reptrans, ClientHandlingProcess cliproc, Cryptography crypto, ReplicaPeerGroup repgroup, BFTInvocationReplica invrep, MessageMapper mapper)
    {
        try
        {
            int ncounters = m_order.getUseRotatingLeader() ? m_nreplicas : 2;
            HybsterCertifiers certs = new HybsterCertifiers( m_strcert, m_stdcert, repgroup, shardno,
                    crypto.getTss(), ncounters, mapper, crypto, invrep );

            return new HybsterShard( master, shardno, this, certs, repgroup, extmanager, cliproc, reptrans, mapper );
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
