package reptor.measr.compose;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import reptor.measr.IntervalObserver;
import reptor.measr.generate.IntervalGenerator;


public class AsyncMeasurementTask implements RunnableFuture<Object>
{
    private final IntervalGenerator m_intgen;
    private final FutureTask<?>     m_innertask;


    public AsyncMeasurementTask(long intdur, int intcnt, IntervalObserver intobs)
    {
        m_intgen = new IntervalGenerator( intdur, intcnt, intobs );
        m_innertask = new FutureTask<>( m_intgen, null );
    }


    public AsyncMeasurementThread createThread()
    {
        return new AsyncMeasurementThread( this );
    }


    public AsyncMeasurementThread createThread(String name, boolean isdaemon)
    {
        AsyncMeasurementThread thread = name != null ?
                new AsyncMeasurementThread( this, name ) : new AsyncMeasurementThread( this );
        thread.setDaemon( isdaemon );

        return thread;
    }


    @Override
    public void run()
    {
        m_innertask.run();
    }


    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        m_intgen.cancel();

        return m_innertask.cancel( mayInterruptIfRunning );
    }


    @Override
    public boolean isCancelled()
    {
        return m_innertask.isCancelled();
    }


    @Override
    public boolean isDone()
    {
        return m_innertask.isDone();
    }


    @Override
    public Object get() throws InterruptedException, ExecutionException
    {
        m_innertask.get();

        return null;
    }


    @Override
    public Object get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        m_innertask.get( timeout, unit );

        return null;
    }
}
