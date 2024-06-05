package reptor.bench.compose;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.LongConsumer;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkClient;
import reptor.bench.BenchmarkClient.BenchmarkClientHandler;
import reptor.bench.LoadGenerator;
import reptor.bench.LoadGenerator.StartBenchmark;
import reptor.chronos.ChronosAddress;
import reptor.chronos.schedule.GenericScheduler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.invoke.InvocationClient;
import reptor.replct.invoke.InvocationClientProvider;


public class Benchmarker extends AbstractBenchmarker implements BenchmarkClientHandler, ChronosAddress
{

    private final CountDownLatch    m_clisready;
    private final LoadGenerator[]   m_loadgens;


    public Benchmarker(ReplicationBenchmark sysconf, DomainGroup domgroup, Benchmark bench, InvocationClient invcli, List<Short> clients,
                       long intdur, int intcnt, int delints, int recints)
                               throws IOException
    {
        super( clients, domgroup.getNumberOfDomains(), intdur, intcnt, delints, recints );

        int ngens  = domgroup.getNumberOfDomains();
        m_loadgens = new LoadGenerator[ ngens ];

        for( short genno=0; genno<ngens; genno++ )
            m_loadgens[ genno ] = createClients( sysconf, bench, genno, ngens, domgroup.getMaster( genno ), invcli );

        m_clisready = new CountDownLatch( clients.size() );
    }


    private LoadGenerator createClients(ReplicationBenchmark sysconf, Benchmark bench, short genno, int ngens,
                                        GenericScheduler<SelectorDomainContext> sched,
                                        InvocationClient invcli) throws IOException
    {
        int   nshardclients = m_clients.size()/ngens + ( genno<( m_clients.size()%ngens ) ? 1 : 0 );
        int   maxinvs       = invcli.getInvocation().getInvocationWindowSize();

        LoadGenerator loadgen = new LoadGenerator( sched.getContext(), genno, nshardclients, maxinvs );
        InvocationClientProvider invprov = invcli.createInvocationProvider();

        for( int i=0; i<nshardclients; i++ )
        {
            short clino = m_clients.get( genno + i*ngens );

            // Invocation handlers start to connect right when they are created.
            LongConsumer    clisnk = m_meas.createMeasuringObject( clino, genno );
            BenchmarkClient client = new BenchmarkClient( loadgen, clino, bench, invprov, this, clisnk );

            loadgen.initClient( i, client );
        }

        sched.registerTask( loadgen );

        return loadgen;
    }


    @Override
    public void clientConnected(BenchmarkClient client)
    {
        m_clisready.countDown();
    }


    @Override
    public void awaitClientConnections() throws InterruptedException
    {
        m_clisready.await();
    }


    @Override
    public void startBenchmark()
    {
        for( LoadGenerator loadgen : m_loadgens )
            loadgen.createChannel( this ).enqueueMessage( new StartBenchmark() );

        super.startBenchmark();
    }

}
