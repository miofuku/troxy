package reptor.measr;

import java.util.Objects;


public interface LongIntervalValueSink extends LongValueSink, IntervalObserver
{
    static final LongIntervalValueSink EMPTY = new LongIntervalValueSink()
                                             {
                                                 @Override
                                                 public void intervalStarted()
                                                 {
                                                 }


                                                 @Override
                                                 public void intervalEnded()
                                                 {
                                                 }


                                                 @Override
                                                 public void intervalCancelled()
                                                 {
                                                 }


                                                 @Override
                                                 public void reset()
                                                 {
                                                 }


                                                 @Override
                                                 public void accept(long value)
                                                 {
                                                 }
                                             };


    static boolean isNullOrEmpty(Object obj)
    {
        return obj == null || obj == EMPTY;
    }


    @FunctionalInterface
    static interface CompositeFactory<T>
    {
        T createComposite(T o0, T o1);
    }


    static <T> T createCompositeIfNecessary(CompositeFactory<T> compfac, T o0, T o1)
    {
        Objects.requireNonNull( compfac, "compfac" );

        boolean has0 = !LongIntervalValueSink.isNullOrEmpty( o0 );
        boolean has1 = !LongIntervalValueSink.isNullOrEmpty( o1 );

        if( has0 && has1 )
            return compfac.createComposite( o0, o1 );
        else if( has0 )
            return o0;
        else if( has1 )
            return o1;
        else
            return null;
    }
}
