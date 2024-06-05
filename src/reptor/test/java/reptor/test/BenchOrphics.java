package reptor.test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State( Scope.Thread )
public class BenchOrphics
{
    private static final int NUMBER_OF_INSTANCES = 10000;


    private static class Config
    {
        private final int m_value;
        private final int m_value2;
        private int m_valuemut;

        private final InstanceI[]  m_insts;


        public Config(int value, int value2)
        {
            m_value    = value;
            m_value2   = value2;
            m_valuemut = value;

            m_insts = new InstanceI[ NUMBER_OF_INSTANCES ];
            Arrays.setAll( m_insts, i -> create() );
        }

        public Config(Config config)
        {
            m_value    = config.m_value;
            m_value2   = config.m_value2;
            m_valuemut = config.m_valuemut;

            m_insts = null;
        }

        public int value()
        {
            return m_value;
        }

        public int value2()
        {
            return m_value2;
        }

        public int valueMutable()
        {
            return m_valuemut;
        }

        public InstanceI create()
        {
            return new InstanceI();
        }

        public class InstanceI
        {
            public int invoke()
            {
                return value() + value2();
            }

            public int invokeMember()
            {
                return m_value + m_value2;
            }
        }

        public void invokeInner()
        {
            for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
                m_insts[ i ].invoke();
        }

        public void invokeInnerMember()
        {
            for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
                m_insts[ i ].invokeMember();
        }

    }

    private static class InstanceC extends Config
    {
        public InstanceC(Config config)
        {
            super( config );
        }

        public int invoke()
        {
            return value() + value2();
        }

        public int invokeMutable()
        {
            return valueMutable() + value2();
        }
    }


    private static class InstanceF
    {
        public int invoke(Config config)
        {
            return config.value() + config.value2();
        }
    }


    private static class Instance
    {
        private final int m_value  = 42;
        private final int m_value2 = 23;
        private final P   m_cntxt;

        public Instance(P cntxt)
        {
            m_cntxt = cntxt;
        }

        public int invokeConst()
        {
            return 42 + 23;
        }

        public int invokeMember()
        {
            return m_value + m_value2;
        }

        private int value()
        {
            return m_value;
        }

        private int value2()
        {
            return m_value2;
        }

        public int invokeWrapped()
        {
            return value() + value2();
        }

        public int invokeCntxtMember()
        {
            return m_cntxt.VALUE + m_cntxt.VALUE2;
        }

        public int invokeCntxtMethod()
        {
            return m_cntxt.value() + m_cntxt.value2();
        }
    }

    private static class P
    {
        public final int VALUE;
        public final int VALUE2;

        public P(int v, int v2)
        {
            VALUE  = v;
            VALUE2 = v2;
        }

        public int value()
        {
            return VALUE;
        }

        public int value2()
        {
            return VALUE2;
        }
    }

    private final Config              m_config;
    private final Instance[]          m_insts;
    private final InstanceC[]         m_instsc;
    private final InstanceF[]         m_instsf;
    private final Config.InstanceI[]  m_instsi;

    public BenchOrphics()
    {
        P cntxt = new P( 42, 23 );
        m_insts = new Instance[ NUMBER_OF_INSTANCES ];
        Arrays.setAll( m_insts, i -> new Instance( cntxt ) );

        m_config = new Config( 42, 23 );
        m_instsc = new InstanceC[ NUMBER_OF_INSTANCES ];
        Arrays.setAll( m_instsc, i -> new InstanceC( m_config ) );

        m_instsi = new Config.InstanceI[ NUMBER_OF_INSTANCES ];
        Arrays.setAll( m_instsi, i -> m_config.create() );

        m_instsf = new InstanceF[ NUMBER_OF_INSTANCES ];
        Arrays.setAll( m_instsf, i -> new InstanceF() );
    }


    @Benchmark
    public void invokeConst()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_insts[ i ].invokeConst();
    }


    @Benchmark
    public void invokeMember()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_insts[ i ].invokeMember();
    }


    @Benchmark
    public void invokeWrapped()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_insts[ i ].invokeWrapped();
    }


    @Benchmark
    public void invokeCntxtMember()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_insts[ i ].invokeCntxtMember();
    }


    @Benchmark
    public void invokeCntxtMethod()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_insts[ i ].invokeCntxtMethod();
    }


    @Benchmark
    public void invokeConfig()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_instsc[ i ].invoke();
    }


    @Benchmark
    public void invokeMutable()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_instsc[ i ].invokeMutable();
    }

    @Benchmark
    public void invokeBound()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_instsi[ i ].invoke();
    }


    @Benchmark
    public void invokeInner()
    {
        m_config.invokeInner();
    }


    @Benchmark
    public void invokeInnerMember()
    {
        m_config.invokeInnerMember();
    }


    @Benchmark
    public void invokeFly()
    {
        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            m_instsf[ i ].invoke( m_config );
    }


    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchOrphics.class.getSimpleName() )
                            .warmupIterations( 5 )
                            .measurementIterations( 5 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
