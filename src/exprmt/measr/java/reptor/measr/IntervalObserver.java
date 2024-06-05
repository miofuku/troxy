package reptor.measr;

public interface IntervalObserver
{
    static final IntervalObserver EMPTY = new IntervalObserver()
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
                                        };


    // -- When the last interval was not explicitly stopped, it is implicitly at this point.
    void intervalStarted();


    void intervalEnded();


    void intervalCancelled();
}
