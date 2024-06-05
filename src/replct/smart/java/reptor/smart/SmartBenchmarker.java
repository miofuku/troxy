package reptor.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SmartBenchmarker extends AbstractBenchmarker
{

    private static final Logger s_logger = LoggerFactory.getLogger( SmartBenchmarker.class );

    private final String cfgdir;


    public SmartBenchmarker(short id, String cfgdir)
    {
        super( id );

        this.cfgdir = cfgdir;
    }


    private static class SmartProxyAdapter implements ClientProxy<InvocationContext>
    {
//        private final ServiceProxy proxy;


        public SmartProxyAdapter(int id, String cfgdir)
        {
//            proxy = new ServiceProxy( id, cfgdir );
        }


//        @Override
//        public ImmutableData invoke(ImmutableData request) throws Exception
//        {
//            assert request.arrayOffset()==0;
//
//            return ImmutableData.wrap( proxy.invokeOrdered( request.array() ) );
//        }


        @Override
        public void startInvocation(InvocationContext invocation)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public boolean isReady()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean execute()
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public InvocationContext pollResult()
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public int getNumberOfOngoingInvocations()
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public int getMaximumNumberOfInvocations()
        {
            throw new UnsupportedOperationException();
        }
    }


    @Override
    protected void startClients(Collection<LoadGenerator> clientrecs, long intdur, int intcnt)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public Collection<LoadGenerator>
            initClients(int clientidbase, int clientcnt, String appname, TransmissionMeter transmeter,
                    LongConsumer reqdurobs, int delints, int recints, List<LongIntervalValueSink> dommeasures)
    {
        ArrayList<LoadGenerator> clients = new ArrayList<>( Collections.nCopies( clientcnt, null ) );

        for( int i = 0; i < clientcnt; i++ )
        {
            try
            {
                ClientProxyFactory<InvocationContext> proxyfac = (master, cliid) -> new SmartProxyAdapter( cliid, cfgdir );
                // Create client
                LoadGenerator clientrec = createClient( null, (short) (clientidbase + i), appname, proxyfac, reqdurobs );
                clients.set( i, clientrec );
            }
            catch( Exception e )
            {
                e.printStackTrace();
                System.exit( 1 );
            }
        }

        return clients;
    }


    public static void main(String[] args) throws Exception
    {
        short id = Short.parseShort( args[0] );

        Config.load( id, args[1] );

        String cfgdir = args[2];
        String resultpath = args[3];
        int clientidbase = Integer.parseInt( args[4] );
        int clientcnt = Integer.parseInt( args[5] );
        long durwarm = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[6] ) );
        long durrun = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[7] ) );
        long durcool = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[8] ) );
        String appname = args[9];

        s_logger.info( "ID: " + id );
        s_logger.info( "ConfigDir: " + cfgdir );
        s_logger.info( "ResultPath: " + resultpath );
        s_logger.info( "ClientIDBase: " + clientidbase );
        s_logger.info( "ClientCnt: " + clientcnt );
        s_logger.info( "DurWarm: " + durwarm );
        s_logger.info( "DurRun: " + durrun );
        s_logger.info( "DurCool: " + durcool );

        SmartBenchmarker benchmark = new SmartBenchmarker( id, cfgdir );
        benchmark.runBenchmark( clientidbase, clientcnt, durwarm, durrun, durcool, appname, resultpath );
    }

}
