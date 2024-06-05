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
public class BenchCode
{

    private static final int[] m_array = new int[] { 0, 0 };

    private static final int INDEX = 1;

    private static enum Index
    {
        ZERO    ( 0 ),
        ONE     ( 1 );

        private final int m_value;

        private Index(int value)
        {
            m_value = value;
        }

        public int getValue()
        {
            return m_value;
        }
    }

    public void access(int index)
    {

    }

    @Benchmark
    public void accessConstant()
    {
        m_array[ INDEX ]++;
    }

    @Benchmark
    public void accessEnum()
    {
        m_array[ Index.ONE.getValue() ]++;
    }


    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchCode.class.getSimpleName() )
                            .warmupIterations( 5 )
                            .measurementIterations( 5 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
