package reptor.distrbt.common.data;

import java.nio.ByteBuffer;


public class DataBuffer extends AbstractData<DataBuffer> implements MutableData
{

    public DataBuffer(int capacity)
    {
        super( capacity );
    }


    public DataBuffer(byte[] buffer)
    {
        super( buffer );
    }


    public DataBuffer(ByteBuffer buffer)
    {
        super( buffer );
    }


    public DataBuffer(ByteBuffer buffer, int offset, int size)
    {
        super( buffer, offset, size );
    }


    public DataBuffer(byte[] buffer, int offset, int size)
    {
        super( buffer, offset, size );
    }


    @Override
    protected DataBuffer create(byte[] buffer, int offset, int size)
    {
        return new DataBuffer( buffer, offset, size );
    }


    @Override
    public MutableData mutable()
    {
        return this;
    }


    @Override
    public void readFrom(byte[] in)
    {
        System.arraycopy( in, 0, m_buffer, m_offset, in.length );
    }


    @Override
    public void readFrom(byte[] in, int otheroffset, int size, int offset)
    {
        System.arraycopy( in, otheroffset, m_buffer, m_offset+offset, size );
    }


    @Override
    public void readFrom(Data in)
    {
        System.arraycopy( in.array(), in.arrayOffset(), m_buffer, m_offset, in.size() );
    }


    @Override
    public void readFrom(Data in, int otheroffset, int size, int offset)
    {
        System.arraycopy( in.array(), in.arrayOffset()+otheroffset, m_buffer, m_offset+offset, size );
    }


    @Override
    public void readFrom(ByteBuffer in)
    {
        in.get( m_buffer, m_offset, m_size );
    }


    @Override
    public void readFrom(ByteBuffer in, int size, int offset)
    {
        in.get( m_buffer, m_offset+offset, size );
    }

}
