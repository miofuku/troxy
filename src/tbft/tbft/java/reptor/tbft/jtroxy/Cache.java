package reptor.tbft.jtroxy;

import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by bli on 09.09.17.
 */
public class Cache
{
    private Map<Integer, Integer> histTable = new ConcurrentHashMap<>();
    private short repno;

    public Cache(short repno)
    {
        this.repno = repno;
    }


    private Integer hash(Request request)
    {
        return request.getCommand().hashCode();
    }


    private Integer hash(Reply reply)
    {
        return reply.getResult().hashCode();
    }


    public void insertReply(Request request, Reply reply)
    {
        histTable.put(hash(request), hash(reply));
    }


    public boolean hasRequest(Request request)
    {
        if (histTable.containsKey(hash(request)))
            return true;

        return false;
    }


    public boolean matchCache(Request request, Reply reply)
    {
        Integer rephash = histTable.get(hash(request));

        if (rephash!=null && rephash.equals(hash(reply)))
            return true;

        return false;
    }


    public void clearCache(Request request)
    {
        histTable.remove(hash(request));
    }

    public Integer getReply(Request request)
    {
        Integer reply = histTable.get(hash(request));
        return reply;
    }

}
