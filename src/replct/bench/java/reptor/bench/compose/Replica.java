package reptor.bench.compose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import reptor.bench.Benchmark;
import reptor.chronos.ChronosAddress;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.schedule.GenericScheduler;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.checkpoint.CheckpointNetworkMessage;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.checkpoint.CheckpointMessages.CheckpointStable;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.Ordering;
import reptor.replct.agree.view.ViewChangeNetworkMessage;
import reptor.replct.agree.view.ViewDependentMessage;
import reptor.replct.clients.ClientHandling;
import reptor.replct.clients.ClientHandlingProcess;
import reptor.replct.common.WorkDistribution;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.common.modules.WorkerRoutingMode;
import reptor.replct.execute.Execution;
import reptor.replct.execute.ExecutionExtensions;
import reptor.replct.execute.Executor;
import reptor.replct.invoke.ClientToWorkerAssignment;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.network.Network;
import reptor.replct.network.ReplicaNetworkProcess;
import reptor.replct.network.ReplicaNetworkWorker;
import reptor.replct.network.ReplicaPeers;
import reptor.replct.replicate.Replication;
import reptor.replct.secure.Cryptography;
import reptor.replct.service.ServiceInstance;


public class Replica implements ChronosAddress
{

    private final ReplicaPeerGroup          m_repgroup;
    private final Replication               m_replicate;
    private final ReplicaNetworkProcess     m_netproc;
    private final ClientHandlingProcess     m_cliproc;
    private final ReplicaPeers              m_peers;

    private final List<DomainEndpoint<PushMessageSink<Message>>>    m_clintshards;
    private final List<DomainEndpoint<PushMessageSink<Message>>>    m_ordershards;
    private final List<DomainEndpoint<PushMessageSink<Message>>>    m_chkptshards;
    private final List<DomainEndpoint<PushMessageSink<Message>>>    m_vwchgshards;
    private final List<Executor>                                    m_exectshards;
    private final List<ReplicaNetworkWorker>                        m_repnets;



    public Replica(byte repno, ReplicationBenchmark repbench, DomainGroup domgroup,
                   ExecutionExtensions exectexts, OrderExtensions ordexts, InvocationExtensions invexts, NetworkExtensions comexts)
    {
        m_repgroup  = repbench.createPeerGroup( repno );
        m_replicate = repbench.getReplication();

        BFTInvocation  invoke     = repbench.getInvocation();
        Ordering       order      = m_replicate.getOrdering();
        Checkpointing  checkpoint = m_replicate.getCheckpointing();
        Execution      execute    = repbench.getExecuting();
        Network        network    = repbench.getNetworking();
        Cryptography   crypto     = repbench.getCryptography();
        ClientHandling clients    = repbench.getClientHandling();
        Benchmark      bench      = repbench.getBenchmarking();

        ViewDependentMessage firstview  = m_replicate.createInitialStableView( m_repgroup );
        CheckpointStable     firstchkpt = new CheckpointStable( 0L );

        // Create client and replica networks
        BFTInvocationReplica invrep = invoke.createReplica( m_repgroup )
                .invocationExtensions( invexts )
                .connectionObserver( comexts.getConnectionObserver() )
                .activate();

        m_netproc = network.createReplicaProcess( m_repgroup, invrep )
                           .connectionObserver( comexts.getConnectionObserver() )
                           .initWorkers( domgroup.getSchedulers() );
        m_repnets = Arrays.asList( m_netproc.getNetworks() );

        m_cliproc = clients.createProcess( m_repgroup, invrep )
                           .initWorkers( domgroup.getSchedulers() );

        GenericScheduler<SelectorDomainContext> sched;

        // Create stages
        m_exectshards = new ArrayList<>( execute.getNumberOfWorkers() );

        for( short i = 0; i < execute.getNumberOfWorkers(); i++ )
        {
            int exectdom = domgroup.getDomainForTask( TaskType.EXECUTOR, i );

            Set<ChronosAddress> remdomset = new HashSet<>();
            for( int ordershard=0; ordershard<order.getNumberOfWorkers(); ordershard++ )
            {
                int orderdom = domgroup.getDomainForTask( TaskType.ORDER_SHARD, ordershard );

                if( orderdom!=exectdom )
                    remdomset.add( domgroup.addressForDomain( orderdom ) );
            }

            ChronosAddress[] remdoms = remdomset.size()<2 ? null : remdomset.toArray( new ChronosAddress[ remdomset.size() ] );

            sched = domgroup.masterForTask( TaskType.EXECUTOR, i );
            ServiceInstance app = bench.createServiceInstance( repno, i );

            Executor exec = new Executor( sched.getContext(), i, remdoms, app, invoke,
                                          m_replicate.getCheckpointing(), execute.getOrderInstanceDistribution(),
                                          m_repgroup, bench.getNumberOfClients(), exectexts, invexts );

            m_exectshards.add( domgroup.registerTask( sched, exec, TaskType.EXECUTOR, 0 ) );
        }

        m_ordershards = new ArrayList<>( order.getNumberOfWorkers() );

        for( short i = 0; i < order.getNumberOfWorkers(); i++ )
        {
            sched = domgroup.masterForTask( TaskType.ORDER_SHARD, i );

            MulticastLink<NetworkMessage> trans =
                    m_netproc.createTransmissionChannel( order.getLinkedNetworksForOrderShard( i ) );

            ClientHandlingProcess scliproc;

            if( clients.useStandaloneClientShards() )
                scliproc = null;
            else
                scliproc = m_cliproc;

            MessageMapper mapper = repbench.getMapping().createReplicaMessageMapper();

            ProtocolShardModule ordershard = m_replicate.createOrderShardProcessor( sched.getContext(), i, ordexts, trans, scliproc, crypto, m_repgroup, invrep, mapper );
            domgroup.registerTask( sched, ordershard, TaskType.ORDER_SHARD, 0 );

            ordershard.enqueueMessage( firstview );
            ordershard.enqueueMessage( firstchkpt );

            m_ordershards.add( ordershard );
        }

        if( !m_replicate.usesDedicatedCheckpointProcessors() )
            m_chkptshards = m_ordershards;
        else
        {
            m_chkptshards = new ArrayList<>( checkpoint.getNumberOfWorkers() );

            for( short i = 0; i < checkpoint.getNumberOfWorkers(); i++ )
            {
                sched = domgroup.masterForTask( TaskType.CHECKPOINT_SHARD, i );

                MulticastLink<NetworkMessage> trans =
                        m_netproc.createTransmissionChannel( checkpoint.getLinkedNetworksForCheckpointShard( i ) );

                ProtocolShardModule chkptshard =
                        domgroup.registerTask( sched, m_replicate.createCheckpointShardProcessor( sched.getContext(), i, trans ), TaskType.CHECKPOINT_SHARD, 0 );

                chkptshard.enqueueMessage( firstview );

                m_chkptshards.add( chkptshard );
            }
        }

        // View change shards are collocated with order shards.
        if( !m_replicate.usesDedicatedViewChangeProcessors() )
            m_vwchgshards = m_ordershards;
        else
        {
            m_vwchgshards = new ArrayList<>( order.getNumberOfWorkers() );

            for( short i=0; i<order.getNumberOfWorkers() ; i++ )
            {
                sched = domgroup.masterForTask( TaskType.ORDER_SHARD, i );

                MulticastLink<NetworkMessage> trans =
                        m_netproc.createTransmissionChannel( order.getLinkedNetworksForOrderShard( i ) );

                ProtocolShardModule viewshard = m_replicate.createViewChangeShardProcessor( sched.getContext(), i, trans );
                viewshard.enqueueMessage( firstview );

                m_vwchgshards.add( domgroup.registerTask( sched, viewshard, TaskType.VIEW_SHARD, 0 ) );
            }
        }

        m_clintshards = clients.getClientRoutingMode()==WorkerRoutingMode.INDIRECT ?
                m_ordershards : Arrays.asList( m_cliproc.getClientShards() );

        // Init first configuration
        m_peers = new Peers( invoke, order, checkpoint );

        m_netproc.initPeers( m_peers );

        for( Executor exec : m_exectshards )
            exec.initPeers( m_peers );

        for( Object shard : m_ordershards )
            ((ProtocolShardModule) shard).initPeers( m_peers );

        if( m_replicate.usesDedicatedCheckpointProcessors() )
        {
            for( Object shard : m_chkptshards )
                ((ProtocolShardModule) shard).initPeers( m_peers );
        }

        if( m_replicate.usesDedicatedViewChangeProcessors() )
        {
            for( Object shard : m_vwchgshards )
                ((ProtocolShardModule) shard).initPeers( m_peers );
        }

        m_cliproc.initPeers( m_peers );

        m_cliproc.start( firstview );
        m_netproc.start();
    }


    @Override
    public String toString()
    {
        return "RPLCA";
    }


    public byte getNumber()
    {
        return m_repgroup.getReplicaNumber();
    }


    public ReplicaPeerGroup getPeerGroup()
    {
        return m_repgroup;
    }


    public Replication getReplicaProtocol()
    {
        return m_replicate;
    }


    public ReplicaPeers getLocalPeers()
    {
        return m_peers;
    }


    public ClientHandlingProcess getClientHandling()
    {
        return m_cliproc;
    }


    public void awaitReplicaConnections() throws InterruptedException
    {
        m_netproc.awaitReplicaConnections();
    }


    private class Peers implements ReplicaPeers
    {
        private final ClientToWorkerAssignment  m_clitowrk;
        private final boolean                   m_multicastcreate;
        private final WorkDistribution      m_orderdist;
        private final WorkDistribution      m_chkptdist;

        public Peers(BFTInvocation invoke, Ordering order, Checkpointing checkpoint)
        {
            m_orderdist       = order.getOrderInstanceShardDistribution();
            m_clitowrk        = invoke.getClientToWorkerAssignment();
            m_multicastcreate = checkpoint.getMulticastCheckpointCreation();
            m_chkptdist       = checkpoint.getCoordinatorSelection();
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getOrderShards()
        {
            return m_ordershards;
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getCheckpointShards()
        {
            return m_chkptshards;
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getViewChangeShards()
        {
            return m_vwchgshards;
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getExecutors()
        {
            return m_exectshards;
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getClientShards()
        {
            return m_clintshards;
        }

        @Override
        public int getClientShard(short clino)
        {
            return m_clitowrk.getShardForClient( clino );
        }

        @Override
        public int getInternalOrderCoordinator(long orderno)
        {
            return m_orderdist.getStageForUnit( orderno );
        }

        @Override
        public int getInternalCheckpointCoordinator(long orderno)
        {
            return m_chkptdist.getStageForUnit( orderno );
        }

        @Override
        public int getInternalCheckpointCoordinator(CheckpointNetworkMessage msg)
        {
            if( !m_multicastcreate )
                return getInternalCheckpointCoordinator( msg.getOrderNumber() );
            else
                return msg.getShardNumber();
        }

        @Override
        public int getInternalViewChangeCoordinator(int newViewID)
        {
            return 0;
        }

        @Override
        public int getInternalViewChangeHandler(ViewChangeNetworkMessage msg)
        {
            return msg.getShardNumber();
        }

        @Override
        public List<? extends DomainEndpoint<PushMessageSink<Message>>> getReplicaNetworks()
        {
            return m_repnets;
        }
    }

}
