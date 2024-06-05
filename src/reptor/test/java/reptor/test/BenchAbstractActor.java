package reptor.test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State( Scope.Thread )
public class BenchAbstractActor
{

    public interface Executable
    {
        boolean execute();
    }


    public static class ActorC1 implements Executable
    {
        @Override
        public boolean execute()
        {
            for( int i=0; i<5; i++ )
            {
                processMessage( i );
                executeSubjects( i );
            }

            return true;
        }

        protected void processMessage(int i)
        {

        }

        protected void executeSubjects(int i)
        {

        }
    }


    public static class ActorC2 implements Executable
    {
        @Override
        public boolean execute()
        {
            for( int i=0; i<5; i++ )
            {
                processMessage( i );
                executeSubjects( i );
            }

            return true;
        }

        protected void processMessage(int i)
        {

        }

        protected void executeSubjects(int i)
        {

        }
    }


    public static abstract class AbstractActor implements Executable
    {
        @Override
        public boolean execute()
        {
            for( int i=0; i<5; i++ )
            {
                processMessage( i );
                executeSubjects( i );
            }

            return true;
        }

        protected abstract void processMessage(int i);

        protected abstract void executeSubjects(int i);
    }


    public static class ActorA extends AbstractActor
    {

        @Override
        protected void processMessage(int i)
        {
        }

        @Override
        protected void executeSubjects(int i)
        {
        }
    }


    public static class ActorB extends AbstractActor
    {

        @Override
        protected void processMessage(int i)
        {
        }

        @Override
        protected void executeSubjects(int i)
        {
        }
    }


    public static abstract class AbstractActor2 implements Executable
    {
        @Override
        public boolean execute()
        {
            for( int i=0; i<5; i++ )
            {
                processMessage( i );
                executeSubjects( i );
            }

            return true;
        }

        protected abstract void processMessage(int i);

        protected abstract void executeSubjects(int i);
    }


    public static class Actor2 extends AbstractActor2
    {

        @Override
        protected void processMessage(int i)
        {
        }

        @Override
        protected void executeSubjects(int i)
        {
        }
    }


    public static abstract class AbstractActor3 implements Executable
    {
        @Override
        public abstract boolean execute();


        protected boolean doExecute(IntConsumer proc, IntConsumer subj)
        {
            for( int i=0; i<5; i++ )
            {
                proc.accept( i );
                subj.accept( i );
            }

            return true;
        }
    }


    public static class ActorA3 extends AbstractActor3
    {
        @Override
        public boolean execute()
        {
            return doExecute( this::processMessage, this::executeSubjects );
        }

        protected void processMessage(int i)
        {
        }

        protected void executeSubjects(int i)
        {
        }
    }


    public static class ActorB3 extends AbstractActor3
    {
        @Override
        public boolean execute()
        {
            return doExecute( this::processMessage, this::executeSubjects );
        }

        protected void processMessage(int i)
        {
        }

        protected void executeSubjects(int i)
        {
        }
    }


    public static class ActorDeleg implements Executable
    {
        private final ActorB m_deleg = new ActorB();

        @Override
        public boolean execute()
        {
            return m_deleg.execute();
        }
    }


    private static final Executable[] m_actcopy;
    private static final Executable[] m_actpoly;
    private static final Executable[] m_actpolyuni;
    private static final Executable[] m_actsingle;
    private static final Executable[] m_actstrat;
    private static final Executable[] m_actstratuni;
    private static final Executable[] m_actdeleg;


    static
    {
        m_actcopy = new Executable[ 10 ];
        Arrays.setAll( m_actcopy, i -> i%2==1 ? new ActorC1() : new ActorC2() );

        m_actpoly = new Executable[ 10 ];
        Arrays.setAll( m_actpoly, i -> i%2==1 ? new ActorB() : new ActorA() );

        m_actpolyuni = new Executable[ 10 ];
        Arrays.setAll( m_actpolyuni, i -> new ActorB() );

        m_actsingle = new Executable[ 10 ];
        Arrays.setAll( m_actsingle, i -> new Actor2() );

        m_actstrat = new Executable[ 10 ];
        Arrays.setAll( m_actstrat, i -> i%2==1 ? new ActorB3() : new ActorA3() );

        m_actstratuni = new Executable[ 10 ];
        Arrays.setAll( m_actstratuni, i -> new ActorB3() );

        m_actdeleg = new Executable[ 10 ];
        Arrays.setAll( m_actdeleg, i -> new ActorDeleg() );
    }


    @Benchmark
    public void executeCopy()
    {
        for( int i=0; i<m_actcopy.length; i++ )
            m_actcopy[ i ].execute();
    }


    @Benchmark
    public void executePoly()
    {
        for( int i=0; i<m_actpoly.length; i++ )
            m_actpoly[ i ].execute();
    }


    @Benchmark
    public void executePolyUni()
    {
        for( int i=0; i<m_actpolyuni.length; i++ )
            m_actpolyuni[ i ].execute();
    }


    @Benchmark
    public void executeSingle()
    {
        for( int i=0; i<m_actsingle.length; i++ )
            m_actsingle[ i ].execute();
    }


    @Benchmark
    public void executeStrat()
    {
        for( int i=0; i<m_actstrat.length; i++ )
            m_actstrat[ i ].execute();
    }


    @Benchmark
    public void executeStratUni()
    {
        for( int i=0; i<m_actstratuni.length; i++ )
            m_actstratuni[ i ].execute();
    }


    @Benchmark
    public void executeDeleg()
    {
        for( int i=0; i<m_actdeleg.length; i++ )
            m_actdeleg[ i ].execute();
    }



    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchAbstractActor.class.getSimpleName() )
                            .warmupIterations( 5 )
                            .measurementIterations( 5 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
