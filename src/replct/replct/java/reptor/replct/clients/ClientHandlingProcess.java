package reptor.replct.clients;

import java.util.List;
import java.util.Objects;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.schedule.GenericScheduler;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.view.ViewDependentMessage;
import reptor.replct.common.modules.WorkerRoutingMode;
import reptor.replct.invoke.InvocationReplica;


public class ClientHandlingProcess
{

    private final ClientHandling    m_clients;
    private final ReplicaPeerGroup  m_repgroup;
    private final InvocationReplica m_invrep;

    private ClientShard[]               m_clints;


    public ClientHandlingProcess(ClientHandling clients, ReplicaPeerGroup repgroup, InvocationReplica invrep)
    {
        m_clients  = Objects.requireNonNull( clients );
        m_repgroup = Objects.requireNonNull( repgroup );
        m_invrep   = Objects.requireNonNull( invrep );
    }


    public ClientHandlingProcess initWorkers(List<? extends GenericScheduler<SelectorDomainContext>> scheds)
    {
        m_clints = new ClientShard[ m_clients.getNumberOfWorkers() ];

        if( m_clients.useStandaloneClientShards() )
        {
            for( short shardno=0; shardno<m_clints.length; shardno++ )
            {
                GenericScheduler<SelectorDomainContext> sched = scheds.get( m_clients.getDomainForClientShard( shardno ) );
                MessageMapper mapper = m_clients.getMapping().createReplicaMessageMapper();

                m_clints[ shardno ] = createStandaloneClientShard( sched.getContext(), shardno, mapper );
                sched.registerTask( m_clints[ shardno ] );
            }
        }

        return this;
    }


    private ClientShard createStandaloneClientShard(SchedulerContext<? extends SelectorDomainContext> master, short shardno, MessageMapper mapper)
    {
        return createClientShard( master, shardno, mapper, null );
    }


    public ClientShard createClientShard(SchedulerContext<? extends SelectorDomainContext> master, short shardno,
                                         MessageMapper mapper, MulticastLink<? super NetworkMessage> repconn)
    {
        return m_clints[ shardno ] = new ClientShard( master, shardno, mapper, repconn, this );
    }


    public ClientHandlingProcess initPeers(AgreementPeers peers)
    {
        for( ClientShard clint : m_clints )
            clint.initPeers( peers );

        return this;
    }


    public ClientHandlingProcess start(ViewDependentMessage firstview)
    {
        if( m_clients.getClientRoutingMode()!=WorkerRoutingMode.INDIRECT )
            for( ClientShard clint : m_clints )
                clint.enqueueMessage( firstview );

        return this;
    }


    public ClientShard[] getClientShards()
    {
        return m_clints;
    }


    public ClientHandling getClientHandling()
    {
        return m_clients;
    }


    public ReplicaPeerGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    public InvocationReplica getInvocationReplica()
    {
        return m_invrep;
    }

}
