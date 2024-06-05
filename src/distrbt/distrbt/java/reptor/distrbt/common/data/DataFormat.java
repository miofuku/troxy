package reptor.distrbt.common.data;

import java.nio.ByteBuffer;


public interface DataFormat
{

    String format(byte[] bytes, int off, int len);

    default String format(byte[] bytes)
    {
        return format( bytes, 0, bytes.length );
    }

    default String format(ByteBuffer buffer)
    {
        return format( buffer, 0, buffer.remaining() );
    }

    default String format(ByteBuffer buffer, int off, int len)
    {
        return format( buffer.array(), buffer.arrayOffset()+buffer.position()+off, len );
    }

    default String format(Data data)
    {
        return format( data, 0, data.size() );
    }

    default String format(Data data, int off, int len)
    {
        return format( data.array(), data.arrayOffset()+off, len );
    }


    static final DataFormat HEX = new DataFormat()
    {
        @Override
        public String format(byte[] bytes, int off, int len)
        {
            StringBuffer sb = new StringBuffer( bytes.length<<2 );

            for( int i=1; i<=len; i++ )
            {
                int v = bytes[i+off-1] & 0xFF;

                if( v<16 ) sb.append('0');
                sb.append( Integer.toHexString(v) );

                if( i%8==0 && i+1<=len )
                    sb.append( '_' );
            }

            return sb.toString();
        }
    };


    static final DataFormat ARRAY = new DataFormat()
    {
        @Override
        public String format(byte[] bytes, int off, int len)
        {
            StringBuffer sb = new StringBuffer( bytes.length<<2 );

            for( int i=1; i<=len; i++ )
            {
                sb.append( "0x" );

                int v = bytes[i+off-1] & 0xFF;

                if( v<16 ) sb.append('0');
                sb.append( Integer.toHexString(v) );

                if( i+1<=len )
                    sb.append( ", " );
            }

            return sb.toString();
        }
    };

}
