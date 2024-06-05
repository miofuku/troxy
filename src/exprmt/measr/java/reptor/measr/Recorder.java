package reptor.measr;

// -- A recorder is a meter, collector, something that can be started and stopped.
public interface Recorder
{
    // -- Throws an exception when the recorder has been already started.
    void start();


    // -- Throws an exception when the collector has not been started.
    void stop();


    boolean isRecording();
}
