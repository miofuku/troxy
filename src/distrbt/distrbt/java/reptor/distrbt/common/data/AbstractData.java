package reptor.distrbt.common.data;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

import javax.crypto.Mac;

import com.google.common.base.Preconditions;
import com.google.common.hash.PrimitiveSink;

import reptor.jlib.ExtArrays;


public abstract class AbstractData<T extends Data> implements Data
{

    private static final int NUMBER_OF_HASH_ELEMENTS = 16;

    protected byte[] m_buffer;
    protected int    m_offset;
    protected int    m_size;


    public AbstractData(int capacity)
    {
        this( new byte[ capacity ] );
    }


    public AbstractData(byte[] buffer)
    {
        this( buffer, 0, buffer.length );
    }


    public AbstractData(ByteBuffer buffer)
    {
        this( buffer, 0, buffer.remaining() );
    }


    public AbstractData(ByteBuffer buffer, int offset, int size)
    {
        this( buffer.array(), buffer.arrayOffset()+buffer.position()+offset, size );
    }


    public AbstractData(byte[] buffer, int offset, int size)
    {
        Objects.requireNonNull( buffer );
        checkOffset( buffer, offset );
        Preconditions.checkArgument( size>=0 && offset+size<=buffer.length );

        m_buffer = buffer;
        m_offset = offset;
        m_size   = size;
    }


    protected abstract T create(byte[] buffer, int offset, int size);


    private static void checkOffset(byte[] buffer, int offset)
    {
        Preconditions.checkArgument( offset==0 && buffer.length==0 || offset>=0 && offset<buffer.length );
    }


    @Override
    public void writeTo(byte[] out)
    {
        System.arraycopy( m_buffer, m_offset, out, 0, m_size );
    }


    @Override
    public void writeTo(byte[] out, int otheroffset, int size, int offset)
    {
        System.arraycopy( m_buffer, m_offset+offset, out, otheroffset, size );
    }


    @Override
    public void writeTo(MutableData out)
    {
        out.readFrom( this );
    }


    @Override
    public void writeTo(MutableData out, int otheroffset, int size, int offset)
    {
        out.readFrom( this, offset, size, otheroffset );
    }


    @Override
    public void writeTo(ByteBuffer out)
    {
        out.put( m_buffer, m_offset, m_size );
    }


    @Override
    public void writeTo(ByteBuffer out, int offset, int size)
    {
        out.put( m_buffer, m_offset+offset, size );
    }


    @Override
    public void writeTo(PrimitiveSink out)
    {
        out.putBytes( m_buffer, m_offset, m_size );
    }


    @Override
    public void writeTo(PrimitiveSink out, int offset, int size)
    {
        out.putBytes( m_buffer, m_offset+offset, size );
    }


    @Override
    public void writeTo(MessageDigest out)
    {
        out.update( m_buffer, m_offset, m_size );
    }


    @Override
    public void writeTo(MessageDigest out, int offset, int size)
    {
        out.update( m_buffer, m_offset+offset, size );
    }


    @Override
    public void writeTo(Mac out)
    {
        out.update( m_buffer, m_offset, m_size );
    }


    @Override
    public void writeTo(Mac out, int offset, int size)
    {
        out.update( m_buffer, m_offset+offset, size );
    }


    @Override
    public void writeTo(Signature out)
    {
        try
        {
            out.update( m_buffer, m_offset, m_size );
        }
        catch( SignatureException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public void writeTo(Signature out, int offset, int size)
    {
        try
        {
            out.update( m_buffer, m_offset+offset, size );
        }
        catch( SignatureException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public byte[] array()
    {
        return m_buffer;
    }


    @Override
    public int arrayOffset()
    {
        return m_offset;
    }


    @Override
    public int size()
    {
        return m_size;
    }


    @Override
    public void adaptSlice(int delta)
    {
        m_offset += delta;
        m_size   -= delta;
    }


    @Override
    public void adaptSlice(ByteBuffer buffer)
    {
        m_offset = buffer.position();
        m_size   = buffer.remaining();
    }


    @Override
    public void adaptSlice(int offset, int size)
    {
        m_offset = offset;
        m_size   = size;
    }


    @Override
    public void resetSlice()
    {
        m_offset = 0;
        m_size   = m_buffer.length;
    }


    @Override
    public ByteBuffer byteBuffer()
    {
        return ByteBuffer.wrap( m_buffer, m_offset, m_size );
    }


    @Override
    public ByteBuffer byteBuffer(int offset)
    {
        return ByteBuffer.wrap( m_buffer, m_offset+offset, m_size-offset );
    }


    @Override
    public ByteBuffer byteBuffer(int offset, int size)
    {
        return ByteBuffer.wrap( m_buffer, m_offset+offset, size );
    }


    @Override
    public MutableData copy()
    {
        DataBuffer copy = new DataBuffer( m_size );
        copy.readFrom( this );

        return copy;
    }


    @Override
    public ImmutableData immutableCopy()
    {
        return ImmutableData.readFrom( this );
    }


    @Override
    public T slice(int offset)
    {
        return create( m_buffer, m_offset+offset, m_size-offset );
    }


    @Override
    public T slice(int offset, int size)
    {
        return create( m_buffer, m_offset+offset, size );
    }


    @Override
    public boolean matches(byte[] other, int otheroffset, int size, int offset)
    {
        checkOffset( m_buffer, m_offset+offset );
        checkOffset( other, otheroffset );

        return matchesUnchecked( other, otheroffset, size, offset );
    }


    @Override
    public boolean matches(Data other, int otheroffset, int size, int offset)
    {
        return matches( other.array(), other.arrayOffset()+otheroffset, size, offset );
    }


    private boolean matchesUnchecked(byte[] other, int otheroffset, int size, int offset)
    {
        if( m_size-offset<size || other.length-otheroffset<size )
            return false;
        else if( other==m_buffer )
            return otheroffset==m_offset;
        else
            return ExtArrays.equalsUnchecked( other, otheroffset, m_buffer, m_offset+offset, size );
    }


    @Override
    public boolean equals(byte[] other)
    {
        if( other==null || other.length!=m_size )
            return false;

        return matchesUnchecked( other, 0, m_size, 0 );
    }


    @Override
    public boolean equals(Data other)
    {
        if( other==this )
            return true;

        if( other==null || other.size()!=m_size )
            return false;

        return matchesUnchecked( other.array(), other.arrayOffset(), m_size, 0 );
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj instanceof Data )
            return equals( (Data) obj );
        else if( obj instanceof byte[] )
            return equals( (byte[]) obj );
        else
            return false;
    }


    @Override
    public int hashCode()
    {
        int step = NUMBER_OF_HASH_ELEMENTS>1 && NUMBER_OF_HASH_ELEMENTS<m_size && m_size>1 ?
                            ( m_size-1 ) / ( NUMBER_OF_HASH_ELEMENTS-1 ) : 1;

        int hash = Long.hashCode( m_size );
        for( int i=m_offset; i<m_offset+m_size; i+=step )
            hash = 31*hash + m_buffer[ i ];

        return hash;
    }

}
