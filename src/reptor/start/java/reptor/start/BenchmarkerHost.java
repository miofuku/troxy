package reptor.start;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.bench.Benchmark;
import reptor.bench.apply.zero.ZeroBenchmark;
import reptor.bench.apply.zk.ZooKeeperBenchmark;
import reptor.bench.compose.AbstractBenchmarker;
import reptor.bench.compose.Benchmarker;
import reptor.bench.compose.BlockingBenchmarker;
import reptor.bench.compose.DomainGroup;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.bench.measure.Measurements;
import reptor.bench.measure.TransmissionMeter;
import reptor.chronos.domains.DomainThread;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.ReplicaGroup;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.InvocationClient;


//TODO: The domain is supposed to provide the time...
public class BenchmarkerHost extends AbstractHost
{

    private static final boolean    USE_BLOCKING_CLIENTS = false;


    private static final Logger s_logger = LoggerFactory.getLogger( BenchmarkerHost.class );


    public static void main(String[] args) throws Exception
    {
        short   benno       = Short.parseShort( args[ 0 ] );
        File    cfgfile     = new File( args[ 1 ] );
        long    durwarm     = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 2 ] ) );
        long    durrun      = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 3 ] ) );
        long    durcool     = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 4 ] ) );
        int     clinooffset = Integer.parseInt( args[ 5 ] );
        String  benchname   = args[ 6 ];
        String  resdir      = args[ 7 ];

        SettingsReader       reader   = settingsReader( cfgfile );
        ReplicationBenchmark repbench = loadBenchmark( benchname, cfgfile, reader );
        ReplicaGroup         repgroup = repbench.getReplicaGroup();
        Benchmark            bench    = repbench.getBenchmarking();
        Measurements         measure  = repbench.getMeasuring();

        int nbenhosts = reader.getShort( "clients.hosts", (short) 1 );

        if( s_logger.isInfoEnabled() )
        {
            s_logger.info( "ID: {}/{}", benno, nbenhosts );
            s_logger.info( "Requests per client: {}", bench.getNumberOfOpenRequestsPerClient() );
            s_logger.info( "Results path  : {}", resdir );

            if( bench instanceof ZeroBenchmark )
            {
                ZeroBenchmark zerobench = (ZeroBenchmark) bench;

                s_logger.info( "zero.writerate    {}", zerobench.getWriteRate() );
                s_logger.info( "zero.conflictrate {}", zerobench.getConflictRate() );
            }
            else if( bench instanceof ZooKeeperBenchmark )
            {
                ZooKeeperBenchmark zkbench = (ZooKeeperBenchmark) bench;

                s_logger.info( "zk.dsmin     {}", zkbench.getMinimumDataSize() );
                s_logger.info( "zk.dsmax     {}", zkbench.getMaximumDataSize() );
                s_logger.info( "zk.nodes     {}", zkbench.getNumberOfNodes() );
                s_logger.info( "zk.writerate {}", zkbench.getWriteRate() );
                s_logger.info( "zk.lossrate  {}", zkbench.getLossRate() );
            }
        }

        DomainGroup domgroup = DomainGroup.load( reader, "client", "CLIENT", new HashMap<>() );
        domgroup.init();

        long durtot  = durwarm + durrun + durcool;
        long intdur  = TimeUnit.SECONDS.toNanos( 1 );
        int  intcnt  = durrun < 0 ? -1 : (int) (durtot / intdur);
        int  delints = (int) (durwarm / intdur);
        int  recints = (int) (durrun / intdur);

        TransmissionMeter transmeter;
        ConnectionObserver connobs;

        int nclients = bench.getNumberOfClients();

        if( !measure.getMeasureClientTransmission() )
        {
            transmeter = null;
            connobs = ConnectionObserver.EMPTY;
        }
        else
        {
            int ncliforhost = nclients/nbenhosts + ( benno<( nclients % nbenhosts ) ? 1 : 0 );
            int ncons  = ncliforhost * repgroup.size();
            transmeter = new TransmissionMeter( ncons, durwarm, durrun, durcool, false );
            connobs = transmeter.getConnectionObserver();
        }

        InvocationClient invcli = repbench.getInvocation().createClient( s_logger.isInfoEnabled() )
                                                          .connectionObserver( connobs );
        List<Short> clients = determineClientsForBenchmarker( repbench, benno, nbenhosts, nclients, clinooffset+repgroup.size() );

        for( short clino : clients )
            repbench.getCryptography().initKeysForClient( clino );
        repbench.getCryptography().activate();

        AbstractBenchmarker host;

        if( USE_BLOCKING_CLIENTS )
        {
            BlockingBenchmarker bb = new BlockingBenchmarker( repbench, bench, invcli, clients, intdur, intcnt, delints, recints );
            bb.activate();

            if( s_logger.isInfoEnabled() )
                logAssignmentSummary( invcli.getSummary() );

            bb.start();

            host = bb;
        }
        else
        {
            host = new Benchmarker( repbench, domgroup, bench, invcli, clients, intdur, intcnt, delints, recints );
            host.activate();

            domgroup.activate();

            DomainThread[] domains = domgroup.getDomains();

            if( s_logger.isInfoEnabled() )
            {
                logAssignmentSummary( invcli.getSummary() );
                printSchedulingConfig( domgroup, domains );
            }

            domgroup.start();
        }

        host.awaitClientConnections();

        InetSocketAddress ctrladdr = reader.getAddress( "addresses.clients." + benno + ".control" );
        new ControlPort( ctrladdr ).waitForGo();

        host.startBenchmark();

        if( durtot>=TimeUnit.SECONDS.toNanos( 10 ) )
        {
            try
            {
                Path respath = FileSystems.getDefault().getPath( resdir, "client" + benno + ".log" );
                System.out.println( "Write results to " + respath );
                host.saveTotalResults( respath );

                respath = FileSystems.getDefault().getPath( resdir, "client" + benno + "-clients.log" );
                System.out.println( "Write results to " + respath );
                host.saveClientRequestDurations( respath );

                if( transmeter!=null )
                {
                    respath = FileSystems.getDefault().getPath( resdir, "client" + benno + "-trans.log" );
                    System.out.println( "Write results to " + respath );
                    transmeter.saveResults( respath );
                }
            }
            catch( IOException e )
            {
                throw new IllegalStateException( e );
            }
        }

        System.out.println( "Exit " + new Date() );

        System.exit( 0 );
    }


    private static List<Short> determineClientsForBenchmarker(ReplicationBenchmark sysconf, int benchid, int nbenhosts,
                                                              int nclients, int clientidbase)
    {
        List<Short> clientids = new ArrayList<>();
        Random      rand      = new Random( 42 );

        for( short i=0; i<nclients; i+=nbenhosts )
        {
            int cliidoff = i + ( ( rand.nextInt( 100 )+benchid ) % nbenhosts );

            if( cliidoff>=nclients )
                break;

            clientids.add( (short) ( clientidbase+cliidoff ) );
        }

        return clientids;
    }


    private static void logAssignmentSummary(int[][] sumclients)
    {
        if( sumclients.length==0 || sumclients[ 0 ].length==0 )
            return;

        int[] sumprims = new int[ sumclients.length ];
        int[] sumstages = new int[ sumclients[ 0 ].length ];

        for( int i=0; i<sumprims.length; i++ )
        {
            for( int j=0; j<sumstages.length; j++ )
            {
                sumprims[ i ]  += sumclients[ i ][ j ];
                sumstages[ j ] += sumclients[ i ][ j ];

                s_logger.info( "Number of clients for primary {} stage {}: {}", i, j, sumclients[ i ][ j ] );
            }
        }

        for( int i=0; i<sumprims.length; i++ )
            s_logger.info( "Number of clients for primary {}: {}", i, sumprims[ i ] );
        for( int i=0; i<sumstages.length; i++ )
            s_logger.info( "Number of clients for stage {}: {}", i, sumstages[ i ] );

    }
}
