package reptor.distrbt.io;

import java.nio.ByteBuffer;


//Buffer is kept in reading mode.
public class CompactingSourceBuffers
{

    public static void clear(ByteBuffer buffer)
    {
        buffer.clear();
        buffer.limit( 0 );
    }


    public static int getStateSize(ByteBuffer buffer)
    {
        return buffer.remaining();
    }


    public static void saveState(ByteBuffer buffer, ByteBuffer dst)
    {
        buffer.mark();
        dst.put( buffer );
        buffer.reset();
    }


    public static void installState(ByteBuffer buffer, ByteBuffer src)
    {
        buffer.compact();
        buffer.put( src );
        buffer.flip();
    }


    public static void replaceBuffer(ByteBuffer buffer, ByteBuffer newbuffer)
    {
        newbuffer.put( buffer );
        newbuffer.flip();
    }


    public static int getAvailableBufferSize(ByteBuffer buffer)
    {
        return buffer.capacity() - buffer.limit() + buffer.position();
    }


    public static ByteBuffer startPreparation(ByteBuffer buffer)
    {
        buffer.compact();

        return buffer;
    }


    public static void finishPreparation(ByteBuffer buffer)
    {
        buffer.flip();
    }

}