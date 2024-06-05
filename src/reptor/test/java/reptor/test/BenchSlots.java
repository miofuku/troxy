package reptor.test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State( Scope.Thread )
public class BenchSlots
{

    private static final int WINDOW_SIZE = 1000;


    private static class ObjectSlots<V>
    {
        private final Object[] m_slots;

        public ObjectSlots(int size, IntFunction<? extends V> generator)
        {
            m_slots = new Object[ size ];
            Arrays.setAll( m_slots, generator );
        }

        @SuppressWarnings("unchecked")
        public V getSlotByIndex(int index)
        {
            return (V) m_slots[ index ];
        }
    }


    private static class TypedSlots<V>
    {
        private final V[] m_slots;
        private final int m_nslots;

        public TypedSlots(V[] slots)
        {
            m_slots  = Objects.requireNonNull( slots );
            m_nslots = slots.length;
        }

        public V getSlotByIndex(int index)
        {
            return m_slots[ index ];
        }

        public int getSlotIndexWithField(long slotno)
        {
            return (int) ( slotno % m_nslots );
        }

        public int getSlotIndexWithLenght(long slotno)
        {
            return (int) ( slotno % m_slots.length );
        }


        public int getSlotIndexWithMask(long slotno)
        {
            return (int) ( slotno ) & 0xFF;
        }
    }


    private final ObjectSlots<ArrayList<Integer>>   m_objslots;
    private final TypedSlots<ArrayList<Integer>>    m_typslots;


    public BenchSlots()
    {
        m_objslots = new ObjectSlots<>( WINDOW_SIZE, slotno -> new ArrayList<Integer>() );

        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] slots = (ArrayList<Integer>[]) Array.newInstance( ArrayList.class, WINDOW_SIZE );
        m_typslots = new TypedSlots<>( slots );
    }


    @Benchmark
    public void objectSlots()
    {
        m_objslots.getSlotByIndex( 0 );
    }


    @Benchmark
    public void typedSlots()
    {
        m_typslots.getSlotByIndex( 0 );
    }


    @Benchmark
    public void fieldIndex()
    {
        m_typslots.getSlotIndexWithField( 0 );
    }


    @Benchmark
    public void lenghtIndex()
    {
        m_typslots.getSlotIndexWithLenght( 0 );
    }


    @Benchmark
    public void maskIndex()
    {
        m_typslots.getSlotIndexWithMask( 0 );
    }


    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                            .include( BenchSlots.class.getSimpleName() )
                            .warmupIterations( 10 )
                            .measurementIterations( 30 )
                            .timeUnit( TimeUnit.MICROSECONDS )
                            .forks( 1 )
                            .build();

        new Runner( opt ).run();
    }

}
