package reptor.chronos.com;

import java.util.Queue;


public interface UnbufferedMessageSink<M> extends CommunicationSink
{
    boolean     canProcessMessages();
    void        processMessages(Queue<M> src);
}
