package reptor.bench.compose;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.bench.Benchmark;
import reptor.bench.apply.counter.CounterBenchmark;
import reptor.bench.apply.http.HttpBenchmark;
import reptor.bench.apply.ycsb.YCSBBenchmark;
import reptor.bench.apply.zero.ZeroBenchmark;
import reptor.bench.apply.zk.ZooKeeperBenchmark;
import reptor.bench.measure.Measurements;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.jlib.hash.HashAlgorithm;
import reptor.replct.ReplicaGroup;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.order.Ordering;
import reptor.replct.clients.ClientHandling;
import reptor.replct.common.modules.WorkerRoutingMode;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Connections;
import reptor.replct.execute.Execution;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.StandardBFTInvocation;
import reptor.replct.map.Mapping;
import reptor.replct.network.Network;
import reptor.replct.replicate.Replication;
import reptor.replct.replicate.hybster.suite.HybsterReplication;
import reptor.replct.replicate.pbft.suite.PbftReplication;
import reptor.replct.secure.Certification;
import reptor.replct.secure.Cryptography;
import reptor.replct.secure.CryptographyEnvironment;
import reptor.tbft.compose.Troxies;
import reptor.tbft.invoke.TransBFTInvocation;
import reptor.tbft.invoke.TransBFTInvocation.HandshakeType;


public class ReplicationBenchmark
{

    private final String                m_benchname;
    private final File                  m_envpath;

    private BFTInvocation               m_invoke;
    private ClientHandling              m_clients;
    private Replication                 m_replicate;
    private Execution                   m_execute;
    private Certification               m_certify;
    private Cryptography                m_crypto;
    private Mapping                     m_map;
    private Network                     m_network;
    private Connections                 m_connect;
    private Benchmark                   m_bench;
    private Measurements                m_measure;

    private ReplicaGroup                m_repgroup;

    private boolean     m_isactive = false;


    public ReplicationBenchmark(String benchname, File envpath)
    {
        m_benchname = benchname;
        m_envpath   = Objects.requireNonNull( envpath );
    }


    public ReplicationBenchmark load(SettingsReader reader)
    {
        // TODO: Get rid of this.
        boolean multiexect = false;

        // Replication
        String repprotname = reader.getString( "agreement.protocol", null );

        if( repprotname==null )
            throw new IllegalArgumentException();

        switch( repprotname )
        {
        case "pbftx":
            m_replicate = new PbftReplication( multiexect ).load( reader );
            break;
        case "hybsterx":
            m_replicate = new HybsterReplication( multiexect ).load( reader );
            break;
        default:
            throw new IllegalArgumentException( repprotname );
        }

        Checkpointing checkpoint = m_replicate.getCheckpointing();
        Ordering      order      = m_replicate.getOrdering();
        byte          nreplicas  = m_replicate.getNumberOfReplicas();
        byte          nfaults    = m_replicate.getNumberOfTolerableFaults();
        HashAlgorithm msgdig     = m_replicate.getDefaultMessageDigest();

        // Networking
        m_network = new Network( nreplicas ).load( reader );

        // Replica group
        m_repgroup = new ReplicaGroup( m_network.getReplicas(), nfaults );

        // Benchmark
        switch( m_benchname )
        {
        case "counter":
            m_bench = new CounterBenchmark( nreplicas ).load( reader );
            break;
        case "zero":
            m_bench = new ZeroBenchmark().load( reader );
            break;
        case "zk":
            m_bench = new ZooKeeperBenchmark().load( reader );
            break;
        case "ycsb":
            m_bench = new YCSBBenchmark(true).load( reader );
            break;
        case "http":
            m_bench = new HttpBenchmark().load( reader );
            break;
        default:
            throw new IllegalArgumentException( "Unknown benchmark " + m_benchname );
        }

        // Measurements
        m_measure = new Measurements().load( reader );

        // Execution
        m_execute = new Execution( order.getOrderInstanceShardDistribution(), checkpoint.getCheckpointShardToExecutorMap() )
                                .load( reader );

        Preconditions.checkArgument( multiexect==( m_execute.getNumberOfWorkers()>1 ) );

        // Client handling
        m_clients = new ClientHandling( m_network.getClientEndpoints() ).load( reader );

        // Invocation
        String invprotname = reader.getString( "client.protocol", "bft" );

        switch( invprotname )
        {
        case "bft":
            m_invoke = new StandardBFTInvocation( Authenticating.HMAC_SHA256, msgdig, m_repgroup, m_bench.getNumberOfClients(),
                                                  m_clients.getAddressesForClientShardMap(), m_bench.getNumberOfOpenRequestsPerClient() );
            break;
        case "tbft":
            {
                short nhshandls = (short) ( m_network.getNumberOfEndpointsForClients()*m_network.getNumberOfHandlersPerEndpoint() );

                TransBFTInvocation tinvoke = new TransBFTInvocation( Authenticating.HMAC_SHA256, msgdig, m_repgroup, m_bench.getNumberOfClients(),
                                                                     m_clients.getAddressesForClientShardMap(), m_bench.getNumberOfOpenRequestsPerClient(),
                                                                     nhshandls, m_network::getHandlerID, HandshakeType.ASSIGNMENT );

                tinvoke.troxy( new Troxies( tinvoke ).load( reader ).activate().getTroxyImplementation() );

                m_invoke = tinvoke;

                break;
            }
        default:
            throw new IllegalArgumentException( invprotname );
        }

        m_invoke.load( reader );

        // Connections
        m_connect = new Connections().load( reader );

        // Certification Workers
        m_certify = new Certification().load( reader );

        // Cryptography
        CryptographyEnvironment env = new CryptographyEnvironment( m_envpath );

        Set<KeyType> clitoreptypes = new HashSet<>();
        Set<KeyType> reptoclitypes = new HashSet<>();
        Set<KeyType> reptoreptypes = new HashSet<>();

        m_invoke.addRequiredKeyTypesTo( clitoreptypes, reptoclitypes, reptoreptypes );
        if( m_connect.useSslForClientConnections() )
        {
            clitoreptypes.add( m_connect.getSslType() );
            reptoclitypes.add( m_connect.getSslType() );
        }

        m_replicate.addRequiredKeyTypesTo( clitoreptypes, reptoclitypes, reptoreptypes );
        if( m_connect.useSslForReplicaConnections() )
            reptoreptypes.add( m_connect.getSslType() );

        int ntssperreplica = order.getNumberOfWorkers();
        int clishardoffset = 0;

        if( m_invoke.usesTrustedSubsystem() && m_replicate.getOrdering().getNumberOfWorkers()==m_clients.getNumberOfWorkers() )
            m_clients.clientRouting( WorkerRoutingMode.DIRECT );

        if( m_invoke.usesTrustedSubsystem() && !m_clients.useStandaloneClientShards() )
        {
            ntssperreplica += m_clients.getNumberOfWorkers();
            clishardoffset  = order.getNumberOfWorkers();
        }

        m_crypto = new Cryptography( nreplicas, m_bench.getNumberOfClients(), ntssperreplica, clishardoffset, env,
                                     clitoreptypes, reptoclitypes, reptoreptypes )
                            .load( reader );

        // Mapping
        //   Magics must be consistent with the client side, thus invocation first.
        NetworkMessageRegistryBuilder climsgreg = new NetworkMessageRegistryBuilder();
        m_invoke.registerMessages( climsgreg );

        NetworkMessageRegistryBuilder repmsgreg = new NetworkMessageRegistryBuilder();
        m_invoke.registerMessages( repmsgreg );
        m_replicate.registerMessages( repmsgreg );

        m_map = new Mapping( climsgreg.createRegistry(), repmsgreg.createRegistry(), msgdig ).load( reader );

        // Link and activate components
        m_map.activate();
        // TODO: Cannot be activate here because keys can only be initialized before activation?
//        m_crypto.activate();
        m_certify.activate();
        m_connect.cryptography( m_crypto ).activate();
        m_invoke.mapping( m_map ).cryptography( m_crypto ).connections( m_connect ).ordering( order ).activate();
        m_clients.mapping( m_map ).cryptography( m_crypto ).connections( m_connect ).ordering( order ).activate();
        m_execute.activate();
        m_network.mapping( m_map ).connections( m_connect ).invocation( m_invoke ).activate();
        m_measure.activate();
        m_bench.activate();
        m_replicate.clientHandling( m_clients ).invocation( m_invoke ).activate();

        return this;
    }


    public ReplicationBenchmark activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public ReplicaPeerGroup createPeerGroup(byte repno)
    {
        return new ReplicaPeerGroup( repno, m_repgroup );
    }


    public ReplicaGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    public BFTInvocation getInvocation()
    {
        return m_invoke;
    }


    public ClientHandling getClientHandling()
    {
        return m_clients;
    }


    public Replication getReplication()
    {
        return m_replicate;
    }


    public Execution getExecuting()
    {
        return m_execute;
    }


    public Certification getCertifying()
    {
        return m_certify;
    }


    public Cryptography getCryptography()
    {
        return m_crypto;
    }


    public Mapping getMapping()
    {
        return m_map;
    }


    public Network getNetworking()
    {
        return m_network;
    }


    public Connections getConnecting()
    {
        return m_connect;
    }


    public Benchmark getBenchmarking()
    {
        return m_bench;
    }


    public Measurements getMeasuring()
    {
        return m_measure;
    }

}
