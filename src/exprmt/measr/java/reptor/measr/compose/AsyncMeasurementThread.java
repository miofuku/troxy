package reptor.measr.compose;

import java.util.Objects;


public class AsyncMeasurementThread extends Thread
{
    private AsyncMeasurementTask m_task;


    public AsyncMeasurementThread(AsyncMeasurementTask task)
    {
        super( Objects.requireNonNull( task, "task" ) );

        m_task = task;
    }


    public AsyncMeasurementThread(AsyncMeasurementTask task, String name)
    {
        this( task );

        setName( name );
    }


    public AsyncMeasurementTask getTask()
    {
        return m_task;
    }
}
