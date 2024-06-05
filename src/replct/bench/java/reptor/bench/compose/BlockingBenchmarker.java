package reptor.bench.compose;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.function.LongConsumer;

import reptor.bench.Benchmark;
import reptor.bench.BlockingBenchmarkClient;
import reptor.bench.apply.http.HttpBenchmark;
import reptor.bench.apply.zero.ZeroBenchmark;
import reptor.replct.invoke.BlockingInvocationHandler;
import reptor.replct.invoke.InvocationClient;
import reptor.replct.invoke.bft.ProphecySketcher;


public class BlockingBenchmarker extends AbstractBenchmarker
{

    private Thread[]  m_threads;

    private BlockingBenchmarkClient[] blockingClients;

    private ProphecySketcher sketcher = new ProphecySketcher();

    public BlockingBenchmarker(ReplicationBenchmark sysconf, Benchmark bench, InvocationClient invconf, List<Short> clients,
                               long intdur, int intcnt, int delints, int recints)
                                       throws IOException
    {
        super( clients, clients.size(), intdur, intcnt, delints, recints );

        m_threads = new Thread[ clients.size() ];

        blockingClients = new BlockingBenchmarkClient[ clients.size()+4 ];

        for( int i=0; i<clients.size(); i++ )
            m_threads[ i ] = createBlockingClient( sysconf, bench, i, invconf );
    }


    private Thread createBlockingClient(ReplicationBenchmark sysconf, Benchmark bench, int index, InvocationClient invconf) throws IOException
    {
        short clino = m_clients.get( index );

        BlockingInvocationHandler invhandler = new BlockingInvocationHandler( clino, invconf, sketcher );
        LongConsumer              clisnk     = m_meas.createMeasuringObject( clino, index );
        BlockingBenchmarkClient   client     = new BlockingBenchmarkClient( clino, bench, invhandler, clisnk );

        Runnable runclient = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        while( true )
                        {
                            if (bench instanceof HttpBenchmark)
                                client.invokeHttpService();
                            else {
                                ZeroBenchmark zeroBenchmark = (ZeroBenchmark) bench;
                                if (zeroBenchmark.getProphecy())
                                    client.invokeProphecyService();
                                else
                                    client.invokeService();
                            }
                        }
                    }
                    catch( IOException e )
                    {
                        throw new UnsupportedOperationException( e );
                    }
                }
            };
        blockingClients[clino] = client;
        return new Thread( runclient );
    }


    public void start()
    {
        for( Thread thread : m_threads )
            thread.start();
    }

    @Override
    public void awaitClientConnections() throws InterruptedException
    {
    }

    public void processSocket(int cnt, Socket clisock)
    {
        cnt = cnt % m_threads.length + 3;
        blockingClients[cnt].setSocket(clisock);
    }

}
