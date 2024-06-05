package reptor.replct.invoke.bft;

import com.google.common.hash.Hashing;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bli on 17.08.17.
 */
public class ProphecySketcher
{
    public ProphecySketcher() {}

    private Map<Integer, Integer> sketcher = new HashMap<>();

    private Integer sketch(Data msg)
    {
        return msg.hashCode();
    }

    private Integer sketch(byte[] reply)
    {
        return reply.hashCode();
    }

    public boolean hasRequest(ImmutableData request)
    {
        Integer hashReq = sketch(request);
        if (sketcher.containsKey(hashReq))
            return true;

        return false;
    }

    public boolean compare(ImmutableData request, byte[] reply)
    {
        if (sketcher.get(sketch(request))==sketch(reply))
            return true;

        return false;
    }

    public void update(ImmutableData request, byte[] reply)
    {
        sketcher.put(sketch(request), sketch(reply));
    }

}
