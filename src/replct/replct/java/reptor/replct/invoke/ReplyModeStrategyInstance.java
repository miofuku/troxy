package reptor.replct.invoke;

import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.invoke.InvocationMessages.Request;


public interface ReplyModeStrategyInstance
{
    void initOrdered(long orderno, long locodrno, short leader);
    void initUnordered(short sender, long invno);

    ReplyMode replyMode(Request request, ImmutableData result);
}
