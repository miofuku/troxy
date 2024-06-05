package reptor.measr.meter;

import java.util.Objects;


public interface IntervalResult<V>
{
    long getElapsedTime();


    V getValueSummary();


    static <V> IntervalResult<V> create(long dur, V valsum)
    {
        Objects.requireNonNull( valsum, "valsum" );

        return new IntervalResult<V>()
        {
            @Override
            public long getElapsedTime()
            {
                return dur;
            }


            @Override
            public V getValueSummary()
            {
                return valsum;
            }


            @Override
            public int hashCode()
            {
                int result = 1;
                result = 31 * result + Long.hashCode( dur );
                result = 31 * result + valsum.hashCode();
                return result;
            }


            @Override
            public boolean equals(Object obj)
            {
                if( obj == this )
                    return true;
                else if( obj == null || obj.getClass() != getClass() )
                    return false;
                else
                {
                    IntervalResult<?> is = (IntervalResult<?>) obj;

                    return is.getElapsedTime() == dur && is.getValueSummary().equals( valsum );
                }
            }
        };
    }
}
