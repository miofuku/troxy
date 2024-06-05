package reptor.chronos.com;

import java.util.Queue;

import reptor.chronos.ChronosTask;


public interface BufferedMessageSource<M> extends CommunicationSource, ChronosTask
{
    // Is reset when finishMessageProcessing() is called.
    boolean     hasUnprocessedMessages();
    // Returns true if there are processed or unprocessed messages.
    boolean     hasMessages();
    Queue<M>    startMessageProcessing();
    void        finishMessageProcessing();
}
