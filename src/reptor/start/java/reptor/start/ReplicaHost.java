package reptor.start;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

import reptor.bench.Benchmark;
import reptor.bench.compose.DomainGroup;
import reptor.bench.compose.Replica;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.bench.compose.TaskType;
import reptor.bench.measure.AppliedCheckpointsMeter;
import reptor.bench.measure.CSVIntervalResultWriter;
import reptor.bench.measure.ExecutedRequestMeter;
import reptor.bench.measure.Measurements;
import reptor.bench.measure.ProtocolInstanceMeter;
import reptor.bench.measure.RequestProcessingMeter;
import reptor.bench.measure.TasksMeter;
import reptor.bench.measure.TransmissionMeter;
import reptor.chronos.domains.DomainThread;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.sink.SummaryStatsSink;
import reptor.replct.ReplicaGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.Ordering;
import reptor.replct.clients.ClientHandling;
import reptor.replct.clients.ClientShard;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Connections;
import reptor.replct.execute.Execution;
import reptor.replct.execute.ExecutionExtensions;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.InvocationReplicaHandler;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.StandardBFTInvocation;
import reptor.replct.map.Mapping;
import reptor.replct.network.Network;
import reptor.replct.replicate.Replication;
import reptor.replct.secure.Certification;
import reptor.replct.secure.Cryptography;
import reptor.tbft.invoke.TransBFTInvocation;


public final class ReplicaHost extends AbstractHost
{

    private final static Logger s_logger = LoggerFactory.getLogger( ReplicaHost.class );


    public static void main(String[] args) throws Exception
    {
        byte    repno     = Byte.parseByte( args[ 0 ] );
        File    cfgfile   = new File( args[ 1 ] );
        String  benchname = args[ 2 ];
        long    durwarm   = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 3 ] ) );
        long    durrun    = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 4 ] ) );
        long    durcool   = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 5 ] ) );
        String  resdir    = args[ 6 ];

        SettingsReader       reader  = settingsReader( cfgfile );
        ReplicationBenchmark repbench = loadBenchmark( benchname, cfgfile, reader );

        ClientHandling  clients  = repbench.getClientHandling();
        Benchmark       bench    = repbench.getBenchmarking();
        Measurements    measure  = repbench.getMeasuring();
        Execution       execute  = repbench.getExecuting();
        Ordering        order    = repbench.getReplication().getOrdering();
        ReplicaGroup    repgroup = repbench.getReplicaGroup();
        Network         network  = repbench.getNetworking();

        // Init measurements
        ExecutedRequestMeter exectreqmeter = measure.getMeasureExecutedRequests() ?
                new ExecutedRequestMeter( execute.getNumberOfWorkers(), durwarm, durrun, durcool, true ) : null;
        AppliedCheckpointsMeter appldckpmeter = measure.getMeasureAppliedCheckpoints() ?
                new AppliedCheckpointsMeter( execute.getNumberOfWorkers(), durwarm, durrun, durcool, true ) : null;
        ExecutionExtensions exectexts = new ExecutionExtensions( exectreqmeter, appldckpmeter );

        ProtocolInstanceMeter protinstmeter = measure.getMeasureConsensusInstances() ?
                new ProtocolInstanceMeter( order.getNumberOfWorkers(), durwarm, durrun, durcool, true ) : null;
        OrderExtensions orderexts = new OrderExtensions( protinstmeter );

        int nsendertages = clients.getCertifierForClientShard( 0 ) != null ?
                repbench.getCertifying().getNumberOfWorkers() : clients.getNumberOfWorkers();

        RequestProcessingMeter reqprocmeter = measure.getMeasureProcessedRequests()
                && (repno == 1 || order.getUseRotatingLeader() ) ?
                new RequestProcessingMeter( nsendertages, durwarm, durrun, durcool, true ) : null;
        InvocationExtensions cliexts = new InvocationExtensions( reqprocmeter );

        int ncons = network.getNumberOfReplicaNetworks() * (repgroup.size() - 1) + bench.getNumberOfClients();
        TransmissionMeter transmeter = measure.getMeasureReplicaTransmission() ?
                new TransmissionMeter( ncons, durwarm, durrun, durcool, false ) : null;
        NetworkExtensions comexts = new NetworkExtensions( transmeter );

        // Create domains and components
        Map<TaskType, Integer> ntasks = new HashMap<>();

        ntasks.put( TaskType.NETWORK_ENDPOINT, network.getNumberOfReplicaEndpoints() );
        ntasks.put( TaskType.REPLICA_NETWORK, (repgroup.size() - 1) * network.getNumberOfReplicaNetworks() );
        ntasks.put( TaskType.ORDER_SHARD, order.getNumberOfWorkers() );
        ntasks.put( TaskType.VIEW_SHARD, order.getNumberOfWorkers() );
        ntasks.put( TaskType.WORKER, repbench.getCertifying().getNumberOfWorkers() );
        ntasks.put( TaskType.CLIENT_SHARD, clients.getNumberOfWorkers() );
        ntasks.put( TaskType.CHECKPOINT_SHARD, repbench.getReplication().getCheckpointing().getNumberOfWorkers() );
        ntasks.put( TaskType.EXECUTOR, execute.getNumberOfWorkers() );

        DomainGroup domgroup = DomainGroup.load( reader, "replica", "REPLICA", ntasks );

        domgroup.init();

        repbench.getCryptography().initKeysForReplica( repno ).activate();
        Replica replica = new Replica( repno, repbench, domgroup, exectexts, orderexts, cliexts, comexts );

        domgroup.activate();

        DomainThread[] domains = domgroup.getDomains();

        // Print config
        printConfig( repbench, replica, benchname );
        printSchedulingConfig( domgroup, domains );

        // Start
        domgroup.start();

        // TODO: Establish a coordination orphic
        replica.awaitReplicaConnections();

        // Wait for go
        InetSocketAddress ctrladdr = reader.getAddress( "addresses.server." + repno + ".control" );
        new ControlPort( ctrladdr ).waitForGo();

        // Wait for end
        if( durrun >= 0
                && (exectreqmeter != null || appldckpmeter != null || protinstmeter != null || reqprocmeter != null || transmeter != null) )
        {
            TasksMeter[] meters = new TasksMeter[] { exectreqmeter, appldckpmeter, protinstmeter, reqprocmeter, transmeter };
            long durtot  = durwarm + durrun + durcool;
            long waitdur = durtot + TimeUnit.SECONDS.toNanos( 5 );
            long startts = System.nanoTime();

            for( TasksMeter meter : meters )
            {
                if( meter == null )
                    continue;

                if( !meter.waitForTasks( waitdur, TimeUnit.NANOSECONDS ) )
                    System.out.println( "Meter " + meter.toString() + " timed out" );

                long curts = System.nanoTime();
                waitdur -= curts - startts;

                if( waitdur < TimeUnit.MILLISECONDS.toNanos( 1 ) )
                    break;

                startts = curts;
            }

            if( waitdur > TimeUnit.MILLISECONDS.toNanos( 1 ) )
                Thread.sleep( TimeUnit.NANOSECONDS.toMillis( waitdur ) );

            if( durtot >= TimeUnit.SECONDS.toNanos( 10 ) )
            {
                if( exectreqmeter != null )
                    saveStageMeterResults( repno, exectreqmeter, resdir, "exectreqs" );

                if( appldckpmeter != null )
                    saveStageMeterResults( repno, appldckpmeter, resdir, "appldckps" );

                if( protinstmeter != null )
                    saveStageMeterResults( repno, protinstmeter, resdir, "protinsts" );

                if( reqprocmeter != null )
                    saveStageMeterResults( repno, reqprocmeter, resdir, "repsender" );

                if( transmeter != null )
                {
                    Path respath = FileSystems.getDefault().getPath( resdir,
                            "replica" + repno + "-trans.log" );
                    transmeter.saveResults( respath );
                }
            }

            System.out.println( "Exit " + new Date() );

            System.exit( 0 );
        }
    }


    public static void saveResults(Path respath, IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink> result)
            throws IOException
    {
        try( BufferedWriter bufwriter = Files.newBufferedWriter( respath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
                PrintWriter writer = new PrintWriter( bufwriter ) )
        {
            CSVIntervalResultWriter<SummaryStatsSink> csvwriter =
                    CSVIntervalResultWriter.createSumStatsWriter( writer );

            csvwriter.writeHeader();
            csvwriter.write( result );
        }
    }


    private static void saveStageMeterResults(int replicaid, TasksMeter meter, String resdir, String resname) throws IOException
    {
        for( int i = 0; i < meter.getNumberOfTasks(); i++ )
        {
            Path respath = FileSystems.getDefault().getPath( resdir,
                    "replica" + replicaid + "-" + resname + i + ".log" );

            System.out.println( "Write results to " + respath );

            saveResults( respath, meter.getResult( i, 0 ) );
        }
    }


    private static void printConfig(ReplicationBenchmark sysconf, Replica replica, String appname)
    {
        if( !s_logger.isInfoEnabled() )
            return;

        Replication    replicate  = sysconf.getReplication();
        Ordering       order      = replicate.getOrdering();
        Checkpointing  checkpoint = replicate.getCheckpointing();
        AgreementPeers peers      = replica.getLocalPeers();
        BFTInvocation  invoke     = sysconf.getInvocation();
        Mapping        map        = sysconf.getMapping();
        Connections    connect    = sysconf.getConnecting();
        Network        network    = sysconf.getNetworking();
        Cryptography   crypto     = sysconf.getCryptography();
        Certification  certify    = sysconf.getCertifying();
        ClientHandling clients    = sysconf.getClientHandling();

        byte nreplicas = replicate.getNumberOfReplicas();
        byte nfaults   = replicate.getNumberOfTolerableFaults();

        s_logger.info( "Replica: " + replica.getNumber() + " / " + nreplicas );
        s_logger.info( "Agreement: " + replicate.getAgreement() );
        s_logger.info( "Application: " + appname );

        s_logger.info( "Mapping:" );
        s_logger.info( "  message digest    : " + map.getDigestionAlgorithm().getName() );
        s_logger.info( "  digestion strategy: " + map.getDigestionStrategy() );

        StringBuilder sslinfo = new StringBuilder( "SSL: " );
        if( connect.getSslType()==null )
            sslinfo.append( "none" );
        else
        {
            if( connect.useSslForReplicaConnections() && connect.useSslForClientConnections() )
                sslinfo.append( "replicas/clients" );
            else if( connect.useSslForReplicaConnections() )
                sslinfo.append( "replicas" );
            else if( connect.useSslForClientConnections() )
                sslinfo.append( "clients" );
            else
                sslinfo.append( "none" );

            sslinfo.append( " (" ).append( connect.getSslType() ).append( ")" );
        }

        s_logger.info( sslinfo.toString() );
        s_logger.info( "Certification:" );

        if( invoke instanceof TransBFTInvocation )
            s_logger.info( "  replica (proposal): " + ((TransBFTInvocation) invoke).getTroxyImplementation().getProposalCertification() );
        else
        {
            s_logger.info( "  client  (replica) : " + ((StandardBFTInvocation) invoke).getClientToReplicaCertification() );
            s_logger.info( "  replica (client)  : " + ((StandardBFTInvocation) invoke).getReplicaToClientCertification() );
        }

        s_logger.info( "  replica (standard): " + replicate.getStrongCertification() );
        s_logger.info( "  replica (strong)  : " + replicate.getStandardCertification() );
        s_logger.info( "Trusted subsystem: " + ( crypto.getTss()==null ? "none" : crypto.getTss() ) );
        s_logger.info( "  library: " + crypto.getTssLibrary() );
        s_logger.info( "  enclave: " + crypto.getTssEnclave() );

        s_logger.info( "Ordering:" );
        s_logger.info( "  distribution    : " + order.getOrderInstanceShardDistribution().getClass().getSimpleName() );
        s_logger.info( "  rotate          : " + order.getUseRotatingLeader() );
        s_logger.info( "  batch size      : " + order.getMinumumCommandBatchSize() + " - " + order.getMaximumCommandBatchSize() );
        s_logger.info( "  commit threshold: " + order.getCommitQuorumSize() );
        s_logger.info( "  instance window : " + order.getOrderWindowSizeForShard()
                + " x " +
                order.getNumberOfWorkers() + " = " + order.getOrderWindowSize() );
        s_logger.info( "  proposer window : " + order.getActiveOrderWindowSizeForShard()
                + " x " +
                order.getNumberOfWorkers() + " = " + order.getActiveOrderWindowSize() );

        s_logger.info( "Checkpointing:" );
        s_logger.info( "  interval            : " + checkpoint.getCheckpointInterval() );
        s_logger.info( "  propagate to all    : " + checkpoint.getPropogateToAll() );
        s_logger.info( "  checkpoint threshold: " + checkpoint.getCheckpointQuorumSize() );
        s_logger.info( "  mode                : " + checkpoint.getCheckpointMode() );

        int readqs  = invoke.readReplyQuorumSize( nreplicas, nfaults );
        int writeqs = invoke.writeReplyQuorumSize( nreplicas, nfaults );
        s_logger.info( "Clients:" );
        s_logger.info( "  invocation           : " + invoke.getClass().getSimpleName() );

        if( invoke instanceof TransBFTInvocation )
            s_logger.info( "  troxy                : " + ((TransBFTInvocation) invoke).getTroxyImplementation() );

        s_logger.info( "  distributed contacts : " + invoke.getUseDistributedContacts() );
        s_logger.info( "  read-only opt        : " + invoke.getUseReadOnlyOptimization() );
        s_logger.info( "  reply quorums (rq-wq): " + readqs + "-" + writeqs + "/" + nreplicas );
        s_logger.info( "  reply mode           : " + invoke.getReplyModeStrategy() );
        s_logger.info( "  routing              : " + clients.getClientRoutingMode() );

        s_logger.info( "Network buffer:" );
        s_logger.info( "  client  (replica) : " + connect.getClientToReplicaSendBufferSize() + "<>" + connect.getClientToReplicaReceiveBufferSize() );
        s_logger.info( "  replica (client)  : " + connect.getReplicaToReplicaSendBufferSize() + "<>" + connect.getReplicaToReplicaReceiveBufferSize() );
        s_logger.info( "  replica (replica) : " + connect.getReplicaToClientSendBufferSize() + "<>" + connect.getReplicaToClientReceiveBufferSize() );

        s_logger.info( "Worker verification: " + certify.getUseForVerification() );

        s_logger.info( "Endpoints:" );
        for( short addrno=0, net=0; addrno<network.getNumberOfReplicaEndpoints(); addrno++ )
        {
            StringBuilder sb = new StringBuilder();
            sb.append( "  " ).append( addrno ).append( " " ).append( replica.getPeerGroup().getAddress( addrno ) );

            sb.append( " (" );

            int nnets = network.getNumberOfNetworksForEndpoint( addrno );
            if( nnets>0 )
            {
                sb.append( String.format( "nets %d-%d, ", net, net+nnets-1 ) );
                net += nnets;
            }

            if( network.isEndpointForClients( addrno ) )
                sb.append( "clients, " );

            sb.append( "hs " ).append( network.getHandshakeProtocolForEndpoint( addrno ).toString() );

            sb.append( ")" );

            s_logger.info( sb.toString() );
        }

        for( short i=0; i<order.getNumberOfWorkers(); i++ )
        {
            Object ordershard = peers.getOrderShards().get( i );

            s_logger.info( "Link stage " + ordershard + " with nets "
                            + Arrays.toString( order.getLinkedNetworksForOrderShard( i ) ) );
            s_logger.info( "Link stage " + ordershard + " with "
                            + peers.getExecutors().get( order.getLinkedExecutorForOrderShard( i ) ) );
            s_logger.info( "Link stage " + ordershard + " with workers "
                            + Arrays.toString( order.getLinkedWorkersForOrderShard( i ) ) );
        }

        if( replica.getReplicaProtocol().usesDedicatedCheckpointProcessors() )
        {
            for( short i=0; i<checkpoint.getNumberOfWorkers(); i++ )
            {
                Object chkptshard = peers.getCheckpointShards().get( i );

                s_logger.info( "Link stage " + chkptshard + " with nets "
                                + Arrays.toString( checkpoint.getLinkedNetworksForCheckpointShard( i ) ) );
                s_logger.info( "Link stage " + chkptshard + " with "
                        + peers.getExecutors().get( checkpoint.getLinkedExecutorForCheckpointShard( i ) ) );
            }
        }

        for( short i=0; i<clients.getNumberOfWorkers(); i++ )
        {
            ClientShard  clintshard = replica.getClientHandling().getClientShards()[ i ];
            Set<Integer> orderaddrs = new TreeSet<Integer>();

            for( int o : clients.getOrderShardsForClientShard( i ) )
            {
                s_logger.info( "Link stage " + clintshard + " with " + peers.getOrderShards().get( o ) );

                for( int netno : order.getLinkedNetworksForOrderShard( o ) )
                    orderaddrs.add( network.getEndpointForNetwork( netno ) );
            }

            s_logger.info( "Link stage " + clintshard + " with workers " +
                            Arrays.toString( clients.getCertifierForClientShard( i ) ) );

            s_logger.info( "Link stage " + clintshard + " with addrs " +
                    Arrays.toString( invoke.getClientToWorkerAssignment().getAddressesForClientShard( i ) ) );
            s_logger.info( "      addrs of order stages " + Arrays.toString( Ints.toArray( orderaddrs ) ) );


            s_logger.info( "Clients handled by " + clintshard + " (" + clintshard.getClients().length + ")" );
            for( InvocationReplicaHandler client : clintshard.getClients() )
                s_logger.info( "    " + client );
        }
    }

}
