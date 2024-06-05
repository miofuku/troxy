package reptor.bench.apply.zk;

import java.io.IOException;
import java.nio.ByteBuffer;


public class MWZooKeeperRequest
{
    public int getMessageSize()
    {
        int base = Byte.BYTES + Short.BYTES + Byte.BYTES * path.getBytes().length;
        switch( operation )
        {
        case GET_DATA:
            return base;
        case SET_DATA:
            return base + Integer.BYTES * 2 + Byte.BYTES * data.length;
        default:
            throw new UnsupportedOperationException();
        }
    }


    public byte[] serialize() throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate( getMessageSize() );

        buf.put( (byte) operation.ordinal() );

        byte[] pathdata = path.getBytes();
        buf.putShort( (short) pathdata.length );
        buf.put( pathdata );

        if( operation == MWZooKeeperOperation.SET_DATA )
        {
            buf.putInt( data.length );
            buf.put( data );
            buf.putInt( version );
        }

        return buf.array();
    }


    private static final MWZooKeeperOperation[] opcodes = MWZooKeeperOperation.values();


    public static MWZooKeeperRequest deserialize(byte[] data)
    {
        ByteBuffer in = ByteBuffer.wrap( data );

        MWZooKeeperOperation operation = opcodes[in.get()];

        byte[] pathdata = new byte[in.getShort()];
        in.get( pathdata );
        String path = new String( pathdata );

        int version = 0;
        byte[] nodedata = null;

        if( operation == MWZooKeeperOperation.SET_DATA )
        {
            nodedata = new byte[in.getInt()];
            in.get( nodedata );
            version = in.getInt();
        }

        return new MWZooKeeperRequest( operation, path, version, nodedata, false );
    }


    private final MWZooKeeperOperation operation;
    private final int                  version;
    private String                     path;
    private byte[]                     data;
    private boolean                    ephemeral;

    // Set on server side
    private long                       sessionID;
    private long                       time;
    private int                        nodeIndex;


    public MWZooKeeperRequest(MWZooKeeperOperation operation, String path)
    {
        this( operation, path, -1, null, false );
    }


    public MWZooKeeperRequest(MWZooKeeperOperation operation, String path, int version)
    {
        this( operation, path, version, null, false );
    }


    public MWZooKeeperRequest(MWZooKeeperOperation operation, String path, int version, byte[] payload,
            boolean ephemeral)
    {
        if( operation == null )
            throw new NullPointerException( "'operation' must not be null." );
        // if( path==null )
        // throw new NullPointerException( "'path' must not be null." );

        this.operation = operation;
        this.path = path;
        this.version = version;
        this.data = payload;
    }


    public MWZooKeeperOperation getOperation()
    {
        return operation;
    }


    public void setPath(String path)
    {
        this.path = path;
    }


    public String getPath()
    {
        return path;
    }


    public int getVersion()
    {
        return version;
    }


    public void setData(byte[] data)
    {
        this.data = data;
    }


    public byte[] getData()
    {
        return data;
    }


    public void setEphemeral(boolean value)
    {
        ephemeral = value;
    }


    public boolean getEphemeral()
    {
        return ephemeral;
    }


    public long getSessionID()
    {
        return sessionID;
    }


    public void setSessionID(long sessionID)
    {
        this.sessionID = sessionID;
    }


    public long getTime()
    {
        return time;
    }


    public void setTime(long time)
    {
        this.time = time;
    }


    public void setNodeIndex(int nodeIndex)
    {
        this.nodeIndex = nodeIndex;
    }


    public int getNodeIndex()
    {
        return nodeIndex;
    }


    @Override
    public String toString()
    {
        return "[" + operation + ", " + path + "]";
    }

}
