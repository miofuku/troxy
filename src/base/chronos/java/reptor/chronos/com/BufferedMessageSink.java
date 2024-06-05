package reptor.chronos.com;

import java.util.Queue;

import reptor.chronos.ChronosTask;

//Notifies the master if it can prepare work or if it is ready to execute.
public interface BufferedMessageSink<M> extends CommunicationSink, ChronosTask
{
    boolean     hasRemaining();
    boolean     canPrepare();
    int         getAvailableBufferSize();
    Queue<M>    startPreparation();
    void        finishPreparation();
}
