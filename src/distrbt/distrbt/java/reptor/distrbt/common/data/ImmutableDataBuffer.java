package reptor.distrbt.common.data;

import java.nio.ByteBuffer;


//TODO: ImmutableByteBuffer? ByteSource? instead of (writable) ByteBuffer.
public class ImmutableDataBuffer extends AbstractData<ImmutableDataBuffer> implements ImmutableData, MutableData
{

    public ImmutableDataBuffer(int capacity)
    {
        super( capacity );
    }


    public ImmutableDataBuffer(byte[] buffer)
    {
        super( buffer );
    }


    public ImmutableDataBuffer(ByteBuffer buffer)
    {
        super( buffer );
    }


    public ImmutableDataBuffer(ByteBuffer buffer, int offset, int size)
    {
        super( buffer, offset, size );
    }


    public ImmutableDataBuffer(byte[] buffer, int offset, int size)
    {
        super( buffer, offset, size );
    }


    @Override
    protected ImmutableDataBuffer create(byte[] buffer, int offset, int size)
    {
        return new ImmutableDataBuffer( buffer, offset, size );
    }


    @Override
    public MutableData mutable()
    {
        return this;
    }


    @Override
    public ImmutableData immutableCopy()
    {
        return this;
    }


    @Override
    public void readFrom(byte[] in)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void readFrom(byte[] in, int otheroffset, int size, int offset)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void readFrom(Data in)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void readFrom(Data in, int otheroffset, int size, int offset)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void readFrom(ByteBuffer in)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void readFrom(ByteBuffer in, int size, int offset)
    {
        throw new UnsupportedOperationException();
    }

}
