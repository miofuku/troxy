package reptor.measr.meter;

import reptor.measr.sink.LongStatsSink;


public interface IntervalHistory<I>
{
    int getNumberOfAvailInts();


    // -- index must be between 0 to getNumberOfAvailInts()-1
    I getInterval(int index);


    static IntervalResult<LongStatsSink> summarize(IntervalHistory<IntervalResult<LongStatsSink>> inthis)
    {
        long dursum = 0;
        LongStatsSink valsum = new LongStatsSink();

        for( int i = 0; i < inthis.getNumberOfAvailInts(); i++ )
        {
            dursum += inthis.getInterval( i ).getElapsedTime();
            valsum.add( inthis.getInterval( i ).getValueSummary() );
        }

        return IntervalResult.create( dursum, valsum );
    }
}
