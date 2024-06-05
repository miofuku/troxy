package reptor.test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State( Scope.Thread )
public class BenchOrphics2
{

    private static final int NUMBER_OF_INSTANCES = 1000;

    private interface Shared
    {
        int getValue();
        int getValue2();
        int getValue3();
        int getValue4();

        int calcSum();
    }


    private interface Executable
    {
        int execute();
    }


    private static class SharedStore implements Shared
    {
        private final int m_value;
        private final int m_value2;
        private final int m_value3;
        private final int m_value4;

        public SharedStore(int value, int value2, int value3, int value4)
        {
            m_value  = value;
            m_value2 = value2;
            m_value3 = value3;
            m_value4 = value4;
        }

        @Override
        public final int getValue()
        {
            return m_value;
        }

        @Override
        public final int getValue2()
        {
            return m_value2;
        }

        @Override
        public final int getValue3()
        {
            return m_value3;
        }

        @Override
        public final int getValue4()
        {
            return m_value4;
        }

        @Override
        public final int calcSum()
        {
            return m_value + m_value2 + m_value3 + m_value4;
        }
    }


    private static class Subject implements Executable
    {
        public interface Context
        {
            Shared  getShared();
        }

        private final Context m_cntxt;

        public Subject(Context cntxt)
        {
            m_cntxt = cntxt;
        }

        private int value()
        {
            return m_cntxt.getShared().getValue();
        }

        private int value2()
        {
            return m_cntxt.getShared().getValue2();
        }

        private int value3()
        {
            return m_cntxt.getShared().getValue3();
        }

        private int value4()
        {
            return m_cntxt.getShared().getValue4();
        }

        @Override
        public int execute()
        {
            return value() + value2() + value3() + value4();
        }
    }


    private static class Master implements Executable
    {
        public interface Context extends Subject.Context
        {
        }

        private final Executable[] m_subjects;

        public Master(Context cntxt, Function<Subject.Context, Executable> fac)
        {
            m_subjects = new Executable[ NUMBER_OF_INSTANCES ];
            Arrays.setAll( m_subjects, i -> fac.apply( cntxt ) );
        }

        @Override
        public int execute()
        {
            int sum = 0;

            for( Executable s : m_subjects )
                sum += s.execute();

            return sum;
        }
    }


    private static class Root implements Executable, Master.Context
    {
        private final Shared m_shared;
        private final Master m_master;

        public Root(Function<Subject.Context, Executable> fac)
        {
            m_shared = new SharedStore( 1, 2, 3, 4 );
            m_master = new Master( this, fac );
        }

        @Override
        public int execute()
        {
            return m_master.execute();
        }

        @Override
        public Shared getShared()
        {
            return m_shared;
        }
    }


    private static class SubjectCopy implements Executable
    {
        private final int m_value;
        private final int m_value2;
        private final int m_value3;
        private final int m_value4;

        public SubjectCopy(Subject.Context cntxt)
        {
            m_value  = cntxt.getShared().getValue();
            m_value2 = cntxt.getShared().getValue2();
            m_value3 = cntxt.getShared().getValue3();
            m_value4 = cntxt.getShared().getValue4();
        }

        private int value()
        {
            return m_value;
        }

        private int value2()
        {
            return m_value2;
        }

        private int value3()
        {
            return m_value3;
        }

        private int value4()
        {
            return m_value4;
        }

        @Override
        public int execute()
        {
            return value() + value2() + value3() + value4();
        }
    }


    private static class MasterCopy implements Executable
    {
        private final SubjectCopy[] m_subjects;

        public MasterCopy(Subject.Context cntxt)
        {
            m_subjects = new SubjectCopy[ NUMBER_OF_INSTANCES ];
            Arrays.setAll( m_subjects, i -> new SubjectCopy( cntxt ) );
        }

        @Override
        public final int execute()
        {
            int sum = 0;

            for( SubjectCopy s : m_subjects )
                sum += s.execute();

            return sum;
        }
    }


    private static class RootCopy implements Executable, Master.Context
    {
        private final Shared     m_shared;
        private final MasterCopy m_master;

        public RootCopy()
        {
            m_shared = new SharedStore( 1, 2, 3, 4 );
            m_master = new MasterCopy( this );
        }

        @Override
        public final int execute()
        {
            return m_master.execute();
        }

        @Override
        public final Shared getShared()
        {
            return m_shared;
        }
    }


    private static class SubjectImpl implements Executable
    {
        private final RootImpl m_cntxt;

        public SubjectImpl(RootImpl cntxt)
        {
            m_cntxt = cntxt;
        }

        private int value()
        {
            return m_cntxt.getShared().getValue();
        }

        private int value2()
        {
            return m_cntxt.getShared().getValue2();
        }

        private int value3()
        {
            return m_cntxt.getShared().getValue3();
        }

        private int value4()
        {
            return m_cntxt.getShared().getValue4();
        }

        @Override
        public int execute()
        {
            return value() + value2() + value3() + value4();
        }
    }


    private static class MasterImpl implements Executable
    {
        private final SubjectImpl[] m_subjects;

        public MasterImpl(RootImpl cntxt)
        {
            m_subjects = new SubjectImpl[ NUMBER_OF_INSTANCES ];
            Arrays.setAll( m_subjects, i -> new SubjectImpl( cntxt ) );
        }

        @Override
        public final int execute()
        {
            int sum = 0;

            for( SubjectImpl s : m_subjects )
                sum += s.execute();

            return sum;
        }
    }


    private static class RootImpl implements Executable, Master.Context
    {
        private final SharedStore m_shared;
        private final MasterImpl  m_master;

        public RootImpl()
        {
            m_shared = new SharedStore( 1, 2, 3, 4 );
            m_master = new MasterImpl( this );
        }

        @Override
        public final int execute()
        {
            return m_master.execute();
        }

        @Override
        public final SharedStore getShared()
        {
            return m_shared;
        }
    }



    private final Root     m_root       = new Root( Subject::new );
    private final Root     m_rootgencpy = new Root( SubjectCopy::new );
    private final RootCopy m_rootcpy    = new RootCopy();
    private final RootImpl m_rootimpl   = new RootImpl();

    private final int m_value  = 1;
    private final int m_value2 = 2;
    private final int m_value3 = 3;
    private final int m_value4 = 4;


    @Benchmark
    public int execute()
    {
        return m_root.execute();
    }


    @Benchmark
    public int executeGenericCopy()
    {
        return m_rootgencpy.execute();
    }


    @Benchmark
    public int executeCopy()
    {
        return m_rootcpy.execute();
    }


    @Benchmark
    public int executeImpl()
    {
        return m_rootimpl.execute();
    }


    @Benchmark
    public int executePlain()
    {
        int sum = 0;

        for( int i=0; i<NUMBER_OF_INSTANCES; i++ )
            sum += m_value + m_value2 + m_value3 + m_value4;

        return sum;
    }


    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchOrphics2.class.getSimpleName() )
                            .warmupIterations( 10 )
                            .measurementIterations( 10 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
