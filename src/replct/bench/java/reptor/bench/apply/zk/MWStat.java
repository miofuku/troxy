package reptor.bench.apply.zk;

import java.io.Serializable;
import java.nio.ByteBuffer;


public class MWStat implements Serializable
{

    private static final long serialVersionUID = -3660826920022264419L;
    private int               version;
    private long              lastModified;
    private long              ephemeralOwner;


    public MWStat()
    {
    }


    public MWStat(int version, long ephemeralOwner)
    {
        this.version = version;
        this.ephemeralOwner = ephemeralOwner;
    }


    public static void copy(MWStat sourcStat, MWStat destinationStat)
    {
        if( destinationStat == null )
            return;
        destinationStat.version = sourcStat.version;
        destinationStat.lastModified = sourcStat.lastModified;
        destinationStat.ephemeralOwner = sourcStat.ephemeralOwner;
    }


    public MWStat(ByteBuffer in)
    {
        version = in.getInt();
        lastModified = in.getLong();
    }


    public void serialize(ByteBuffer out)
    {
        out.putInt( version );
        out.putLong( lastModified );
    }


    public int getSerializedSize()
    {
        return Integer.BYTES + Long.BYTES;
    }


    public int getVersion()
    {
        return version;
    }


    public void setVersion(int version)
    {
        this.version = version;
    }


    public long getLastModified()
    {
        return lastModified;
    }


    public void setLastModified(long lastModified)
    {
        this.lastModified = lastModified;
    }


    public long getEphemeralOwner()
    {
        return ephemeralOwner;
    }


    public void setEphemeralOwner(long ephemeralOwner)
    {
        this.ephemeralOwner = ephemeralOwner;
    }

}
