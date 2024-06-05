package reptor.measr.filter;

import reptor.measr.IntervalObserver;
import reptor.measr.Recorder;


public class IntervalRecorder extends IntervalFilter implements Recorder
{
    public static class Factory implements IntervalFilter.Factory<IntervalRecorder>
    {
        protected boolean m_start;


        public Factory(boolean start)
        {
            m_start = start;
        }


        @Override
        public IntervalRecorder create(IntervalObserver intobs, IntervalObserver recobs)
        {
            return new IntervalRecorder( m_start, intobs, recobs );
        }
    }


    public IntervalRecorder(boolean start, IntervalObserver intobs, IntervalObserver recobs)
    {
        super( intobs, recobs );

        if( start )
            startRecording();
    }


    @Override
    public void start()
    {
        if( isRecording() )
            throw new IllegalStateException( "Recorder has been already started." );

        startRecording();
    }


    @Override
    public void stop()
    {
        if( !isRecording() )
            throw new IllegalStateException( "Recorder has not been started." );

        intervalCancelled();
        stopRecording();
    }
}
