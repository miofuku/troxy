package reptor.distrbt.io;

import java.nio.ByteBuffer;


// Buffer is kept in writing mode.
public class CompactingSinkBuffers
{

    public static void clear(ByteBuffer buffer)
    {
        buffer.clear();
    }


    public static int getStateSize(ByteBuffer buffer)
    {
        return buffer.position();
    }


    public static void saveState(ByteBuffer buffer, ByteBuffer dst)
    {
        buffer.flip();
        dst.put( buffer );
        buffer.limit( buffer.capacity() );
    }


    public static void installState(ByteBuffer buffer, ByteBuffer src)
    {
        buffer.put( src );
    }


    public static void replaceBuffer(ByteBuffer buffer, ByteBuffer newbuffer)
    {
        buffer.flip();
        newbuffer.put( buffer );
    }


    public static boolean hasData(ByteBuffer buffer)
    {
        return buffer.position()>0;
    }


    public static ByteBuffer startDataProcessing(ByteBuffer buffer)
    {
        buffer.flip();

        return buffer;
    }


    public static void finishDataProcessing(ByteBuffer buffer)
    {
        buffer.compact();
    }

}
