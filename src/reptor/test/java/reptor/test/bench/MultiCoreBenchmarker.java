package reptor.test.bench;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntFunction;

import reptor.jlib.threading.SystemThread;
import reptor.measr.sink.LongStatsSink;


public class MultiCoreBenchmarker
{

    private final IntFunction<MultiCoreTestObject> m_objfac;
    private final PrintStream                      m_intout;

    private MultiCoreTestObject[]   m_objects;
    private int[]                   m_affs;
    private LongStatsSink[][]       m_ints;

    private int     m_nints         = 10;
    private int     m_intlen        = 1000000;
    private int     m_intmul        = 1;
    private boolean m_threadinit    = true;
    private boolean m_measuresingle = false;
    private boolean m_printints     = false;

    private CountDownLatch m_startsig;


    public MultiCoreBenchmarker(IntFunction<MultiCoreTestObject> objfac)
    {
        this( objfac, System.out );
    }

    public MultiCoreBenchmarker(IntFunction<MultiCoreTestObject> objfac, PrintStream intout)
    {
        m_objfac = Objects.requireNonNull( objfac );
        m_intout = Objects.requireNonNull( intout );
    }


    public void initObjects(int nobjs) throws IOException
    {
        m_objects = new MultiCoreTestObject[ nobjs ];

        if( !m_threadinit )
            for( int objno=0; objno<nobjs; objno++ )
                m_objects[ objno ] = m_objfac.apply( objno );

        m_affs = new int[ nobjs ];
        Arrays.fill( m_affs, -1 );

        initIntervals( m_nints );
    }

    public int getNumberOfObjects()
    {
        return m_objects.length;
    }


    public void initIntervals(int nints)
    {
        if( m_objects!=null )
        {
            m_ints = new LongStatsSink[ m_objects.length ][];
            for( int i=0; i<m_ints.length; i++ )
            {
                m_ints[ i ] = new LongStatsSink[ nints ];
                Arrays.setAll( m_ints[ i ], x -> new LongStatsSink() );
            }
        }

        m_nints = nints;
    }


    public int getNumberOfIntervals()
    {
        return m_nints;
    }

    public LongStatsSink[][] getIntervals()
    {
        return m_ints;
    }


    public void setIntervalLength(int intlen)
    {
        m_intlen = intlen;
    }

    public int getIntervalLength()
    {
        return m_intlen;
    }

    public void setIntervalMultiplier(int intmul)
    {
        m_intmul = intmul;
    }

    public int getIntervalMultiplier()
    {
        return m_intmul;
    }

    public void setMeasureSingleCalls(boolean msc)
    {
        m_measuresingle = msc;
    }

    public void setPrintIntervals(boolean pi)
    {
        m_printints = pi;
    }

    public void setCpuAffinity(short tmid, int cpuid)
    {
        m_affs[ tmid ] = cpuid;
    }

    public void setThreadLocalInit(boolean ti)
    {
        m_threadinit = ti;
    }

    public int getCpuAffinity(short tmid)
    {
        return m_affs[ tmid ];
    }

    public void Run()
    {
        m_startsig = new CountDownLatch( m_objects.length );

        Thread[] benchthreads = new Thread[  m_objects.length ];

        for( short i=0; i<m_objects.length; i++ )
        {
            final short objno = i;
            benchthreads[ i ] = new Thread( () -> RunForObject( objno ) );
        }

        for( Thread t : benchthreads )
            t.start();

        try
        {
            for( Thread t : benchthreads )
                t.join();
        }
        catch( InterruptedException e )
        {
            throw new IllegalStateException( e );
        }
    }


    public void RunForObject(int objno)
    {
        int cpuid = m_affs[ objno ];

        if( cpuid>=0 )
            SystemThread.setThreadAffinity( new int[] { cpuid } );

        final double timefac = 1000000000.0;

        if( m_threadinit )
            m_objects[ objno ] = m_objfac.apply( objno );

        MultiCoreTestObject obj = m_objects[ objno ];

        try
        {
            m_startsig.countDown();
            m_startsig.await();
        }
        catch( InterruptedException e )
        {
            throw new IllegalStateException( e );
        }

        for( LongStatsSink cint : m_ints[ objno ] )
        {
            if( m_measuresingle )
            {
                for( int c=0; c<m_intlen; c++ )
                {
                    long cs = System.nanoTime();
                    obj.invoke();
                    long ce = System.nanoTime();

                    long d = ce-cs;
                    cint.add( m_intmul, d, d, d );
                }

                if( m_printints )
                {
                    double avg = cint.getSum() / (double) cint.getCount();
                    String log = String.format( "%2d (in ns) %9.0f (%9d - %9d) -> ops/sec %7.0f",
                                                objno, avg, cint.getMin(), cint.getMax(), timefac/avg );
                    m_intout.println( log );
                }
            }
            else
            {
                long is = System.nanoTime();

                for( int i=0; i<m_intlen; i++ )
                    obj.invoke();

                long ie = System.nanoTime();

                cint.add( m_intlen*m_intmul, ie-is, 0, 0 );

                if( m_printints )
                {
                    double avg = cint.getSum() / (double) cint.getCount();
                    String log = String.format( "%2d (in ns) %9.0f -> ops/sec %7.0f",
                                                objno, avg, timefac/avg );
                    m_intout.println( log );
                }
            }
        }
    }

}
