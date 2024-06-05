package reptor.test;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State( Scope.Thread )
public class BenchMaster
{

    private interface Task
    {
        int execute();
    }


    private interface Scheduler
    {
        void taskReady(Task task);
    }


    private interface Shared
    {
        int getValue();
        int getValue2();
        int getValue3();
        int getValue4();

        int calcSum();
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


    private static class SubjectArg implements Task
    {
        public interface Context extends Shared
        {
        }

        private final Context   m_cntxt;
        private final Scheduler m_master;

        public SubjectArg(Context cntxt, Scheduler master)
        {
            m_cntxt  = cntxt;
            m_master = master;
        }

        private int value()
        {
            return m_cntxt.getValue();
        }

        private int value2()
        {
            return m_cntxt.getValue2();
        }

        private Scheduler master()
        {
            return m_master;
        }

        @Override
        public int execute()
        {
            master().taskReady( this );

            return value() + value2();
        }
    }


    private static class SubjectMerge implements Task
    {
        public interface Context extends Shared, Scheduler
        {
        }

        private final Context   m_cntxt;

        public SubjectMerge(Context cntxt)
        {
            m_cntxt = cntxt;
        }

        private int value()
        {
            return m_cntxt.getValue();
        }

        private int value2()
        {
            return m_cntxt.getValue2();
        }

        private Scheduler master()
        {
            return m_cntxt;
        }

        @Override
        public int execute()
        {
            master().taskReady( this );

            return value() + value2();
        }
    }


    private static class SubjectContext implements Task
    {
        public interface Context extends Shared
        {
            Scheduler   getMaster();
        }

        private final Context   m_cntxt;

        public SubjectContext(Context cntxt)
        {
            m_cntxt = cntxt;
        }

        private int value()
        {
            return m_cntxt.getValue();
        }

        private int value2()
        {
            return m_cntxt.getValue2();
        }

        private Scheduler master()
        {
            return m_cntxt.getMaster();
        }

        @Override
        public int execute()
        {
            master().taskReady( this );

            return value() + value2();
        }
    }


    public static class DirectMasterArg extends SharedStore implements SubjectArg.Context, Scheduler, Task
    {
        private final SubjectArg m_subject;
        private int m_cnt = 0;

        public DirectMasterArg()
        {
            super( 1, 2, 3, 4 );

            m_subject = new SubjectArg( this, this );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }
    }


    public static class DirectMasterMerge extends SharedStore implements SubjectMerge.Context, Scheduler, Task
    {
        private final SubjectMerge m_subject;
        private int m_cnt = 0;

        public DirectMasterMerge()
        {
            super( 1, 2, 3, 4 );

            m_subject = new SubjectMerge( this );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }
    }


    public static class DirectMasterContext extends SharedStore implements SubjectContext.Context, Scheduler, Task
    {
        private final SubjectContext m_subject;
        private int m_cnt = 0;

        public DirectMasterContext()
        {
            super( 1, 2, 3, 4 );

            m_subject = new SubjectContext( this );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }

        @Override
        public Scheduler getMaster()
        {
            return this;
        }
    }


    public static class CompoundMasterArg implements Scheduler, Task
    {
        private final SubjectArg m_subject;
        private int m_cnt = 0;

        private static class ContextImpl extends SharedStore implements SubjectArg.Context
        {
            public ContextImpl()
            {
                super( 1, 2, 3, 4 );
            }
        }

        public CompoundMasterArg()
        {
            m_subject = new SubjectArg( new ContextImpl(), this );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }
    }


    public static class CompoundMasterMerge implements Scheduler, Task
    {
        private final SubjectMerge m_subject;
        private int m_cnt = 0;

        private static class ContextImpl extends SharedStore implements SubjectMerge.Context
        {
            private final Scheduler m_master;

            public ContextImpl(Scheduler master)
            {
                super( 1, 2, 3, 4 );

                m_master = master;
            }

            @Override
            public void taskReady(Task task)
            {
                m_master.taskReady( task );
            }
        }

        public CompoundMasterMerge()
        {
            m_subject = new SubjectMerge( new ContextImpl( this ) );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }
    }


    public static class CompoundMasterContext implements Scheduler, Task
    {
        private final SubjectContext m_subject;
        private int m_cnt = 0;

        private static class ContextImpl extends SharedStore implements SubjectContext.Context
        {
            private final Scheduler m_master;

            public ContextImpl(Scheduler master)
            {
                super( 1, 2, 3, 4 );

                m_master = master;
            }

            @Override
            public Scheduler getMaster()
            {
                return m_master;
            }
        }

        public CompoundMasterContext()
        {
            m_subject = new SubjectContext( new ContextImpl( this ) );
        }

        public int getCount()
        {
            return m_cnt;
        }

        @Override
        public void taskReady(Task task)
        {
            m_cnt++;
        }

        @Override
        public int execute()
        {
            return m_subject.execute();
        }
    }

    private final DirectMasterArg       m_directarg   = new DirectMasterArg();
    private final DirectMasterMerge     m_directmerge = new DirectMasterMerge();
    private final DirectMasterContext   m_directcntxt = new DirectMasterContext();
    private final CompoundMasterArg     m_comparg     = new CompoundMasterArg();
    private final CompoundMasterMerge   m_compmerge   = new CompoundMasterMerge();
    private final CompoundMasterContext m_compcntxt   = new CompoundMasterContext();


    @Benchmark
    public int executeDirectArg()
    {
        return m_directarg.execute();
    }


    @Benchmark
    public int executeDirectMerge()
    {
        return m_directmerge.execute();
    }


    @Benchmark
    public int executeDirectContext()
    {
        return m_directcntxt.execute();
    }


    @Benchmark
    public int executeCompoundArg()
    {
        return m_comparg.execute();
    }


    @Benchmark
    public int executeCompoundMerge()
    {
        return m_compmerge.execute();
    }


    @Benchmark
    public int executeCompoundContext()
    {
        return m_compcntxt.execute();
    }


    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchMaster.class.getSimpleName() )
                            .warmupIterations( 10 )
                            .measurementIterations( 10 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
