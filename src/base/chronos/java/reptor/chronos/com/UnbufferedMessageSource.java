package reptor.chronos.com;

import java.io.IOException;
import java.util.Queue;


public interface UnbufferedMessageSource<M> extends CommunicationSource
{
    boolean     canRetrieveMessages(int bufsize);
    void        retrieveMessages(Queue<M> dst) throws IOException;
}
