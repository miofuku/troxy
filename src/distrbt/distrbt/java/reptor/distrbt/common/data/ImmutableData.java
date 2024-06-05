package reptor.distrbt.common.data;

import java.nio.ByteBuffer;

import reptor.chronos.ImmutableObject;


public interface ImmutableData extends Data, ImmutableObject
{

    static final ImmutableData EMPTY = new ImmutableDataBuffer( 0 );


    static ImmutableData createFor(byte[] buffer)
    {
        return buffer==null || buffer.length==0 ? EMPTY : new ImmutableDataBuffer( buffer );
    }


    static ImmutableData createFor(ByteBuffer buffer)
    {
        return buffer==null || buffer.remaining()==0 ? EMPTY : new ImmutableDataBuffer( buffer );
    }


    static ImmutableData createFor(ByteBuffer buffer, int size)
    {
        return buffer==null || size==0 || buffer.remaining()==0 ? EMPTY : new ImmutableDataBuffer( buffer, 0, size );
    }


    static ImmutableData wrap(byte[] buffer)
    {
        return new ImmutableDataBuffer( buffer );
    }


    static ImmutableData wrapFrom(ByteBuffer in, int size)
    {
        ImmutableData data = new ImmutableDataBuffer( in, 0, size );

        in.position( in.position()+size );

        return data;
    }


    static ImmutableData readFrom(ByteBuffer in, int size)
    {
        if( size==0 )
            return ImmutableData.EMPTY;
        else
        {
            byte[] copy = new byte[ size ];
            in.get( copy );

            return new ImmutableDataBuffer( copy );
        }
    }


    static ImmutableData readFrom(Data buffer)
    {
        if( buffer.size()==0 )
            return ImmutableData.EMPTY;
        else
        {
            byte[] copy = new byte[ buffer.size() ];
            buffer.writeTo( copy );

            return new ImmutableDataBuffer( copy );
        }
    }


    @Override
    ImmutableData slice(int offset);

    @Override
    ImmutableData slice(int offset, int size);

}
