package reptor.measr.meter;

import reptor.measr.Meter;
import reptor.measr.Recorder;
import reptor.measr.ValueCollector;


// -- A stop watch measures elapsed time.
public class Stopwatch implements Recorder, Meter, ValueCollector
{
    private long m_startts = -1;
    private long m_elapsed = 0;


    @Override
    public void start()
    {
        if( isRecording() )
            throw new IllegalStateException( "Recorder has been already started." );

        m_startts = retrieveTime();
    }


    @Override
    public void stop()
    {
        if( !isRecording() )
            throw new IllegalStateException( "Recorder has not been started." );

        m_elapsed += retrieveTime() - m_startts;
        m_startts = -1;
    }


    public long stopAndReset()
    {
        if( !isRecording() )
            throw new IllegalStateException( "Recorder has not been started." );

        long elapsed = getElapsedTime();

        reset();

        return elapsed;
    }


    public void cancel()
    {
        if( !isRecording() )
            throw new IllegalStateException( "Recorder has not been started." );

        m_startts = -1;
    }


    @Override
    public void reset()
    {
        m_elapsed = 0;
        m_startts = -1;
    }


    @Override
    public boolean isRecording()
    {
        return m_startts != -1;
    }


    public long getElapsedTime()
    {
        return isRecording() ? m_elapsed + retrieveTime() - m_startts : m_elapsed;
    }


    protected long retrieveTime()
    {
        return System.nanoTime();
    }
}
