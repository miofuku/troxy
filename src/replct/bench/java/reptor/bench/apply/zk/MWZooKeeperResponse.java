package reptor.bench.apply.zk;

import java.io.IOException;
import java.nio.ByteBuffer;


public class MWZooKeeperResponse
{
    public MWZooKeeperResponse()
    {
    }


    public int getMessageSize()
    {
        int size = Byte.BYTES;
        if( stat != null )
            size += stat.getSerializedSize();
        if( data != null )
            size += Integer.BYTES + Byte.BYTES * data.length;
        if( path != null )
            size += Short.BYTES + Byte.BYTES * path.getBytes().length;
        return size;
    }


    public byte[] serialize() throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate( getMessageSize() );

        byte flags = 0;
        if( stat != null )
            flags |= 1;
        if( ex != null )
            flags |= 2;
        if( data != null )
            flags |= 4;
        if( path != null )
            flags |= 8;

        buf.put( flags );

        if( stat != null )
            stat.serialize( buf );

        if( data != null )
        {
            buf.putInt( data.length );
            buf.put( data );
        }

        if( path != null )
        {
            byte[] pathdata = path.getBytes();
            buf.putShort( (short) pathdata.length );
            buf.put( pathdata );
        }

        return buf.array();
    }


    public static MWZooKeeperResponse deserialize(byte[] data)
    {
        ByteBuffer in = ByteBuffer.wrap( data );

        MWZooKeeperResponse res = new MWZooKeeperResponse();

        byte flags = in.get();

        if( (flags & 1) != 0 )
            res.setStat( new MWStat( in ) );

        if( (flags & 2) != 0 )
            res.setException( new MWZooKeeperException( null ) );

        if( (flags & 4) != 0 )
        {
            byte[] nodedata = new byte[in.getInt()];
            in.get( nodedata );
            res.setData( nodedata );
        }

        if( (flags & 8) != 0 )
        {
            byte[] pathdata = new byte[in.getShort()];
            in.get( pathdata );
            res.setPath( new String( pathdata ) );
        }

        return res;
    }


    private MWStat               stat;
    private MWZooKeeperException ex;
    private byte[]               data;
    private String               path;


    public void setException(MWZooKeeperException ex)
    {
        this.ex = ex;
    }


    public void setPath(String path)
    {
        this.path = path;
    }


    public String getPath() throws MWZooKeeperException
    {
        if( ex != null )
            throw ex;
        return path;
    }


    public void setStat(MWStat stat)
    {
        this.stat = stat;
    }


    public MWStat getStat() throws MWZooKeeperException
    {
        if( ex != null )
            throw ex;
        return stat;
    }


    public void setData(byte[] data)
    {
        this.data = data;
    }


    public byte[] getData() throws MWZooKeeperException
    {
        if( ex != null )
            throw ex;
        return data;
    }
}
