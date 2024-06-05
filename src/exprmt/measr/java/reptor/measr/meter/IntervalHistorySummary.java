package reptor.measr.meter;

public interface IntervalHistorySummary<S, I> extends IntervalHistory<IntervalResult<I>>
{
    IntervalResult<S> getSummary();


    static <S, I> IntervalHistorySummary<S, I>
            create(IntervalResult<S> sumsink, IntervalHistory<IntervalResult<I>> hissink)
    {
        return new IntervalHistorySummary<S, I>()
        {
            @Override
            public IntervalResult<S> getSummary()
            {
                return sumsink;
            }


            @Override
            public int getNumberOfAvailInts()
            {
                return hissink != null ? hissink.getNumberOfAvailInts() : 0;
            }


            @Override
            public IntervalResult<I> getInterval(int index)
            {
                if( hissink == null )
                    throw new IllegalArgumentException( "No intervals available." );

                return hissink.getInterval( index );
            }
        };
    }
}
