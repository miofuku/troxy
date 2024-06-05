package reptor.smart;

import java.io.IOException;
import java.nio.ByteBuffer;


//           |   message   |
//| size | 0 | wrapped msg |
public class SmartMessageFormatter extends BasicMessageMapper
{

    private static final int PREAMBLE_SIZE = Integer.BYTES + Byte.BYTES;


    public SmartMessageFormatter(NetworkMessageRegistry msgreg, BasicMessageDigestionStrategy.Variant digvariant,
                                 HashAlgorithm hashalgo)
    {
        super( msgreg, digvariant, hashalgo );
    }


    @Override
    public boolean writeMessageTo(ByteBuffer out, NetworkMessage msg)
    {
        if( out.remaining()<PREAMBLE_SIZE+msg.getMessageSize() )
            return false;
        else
        {
            out.putInt( Byte.BYTES + msg.getMessageSize() );
            out.put( (byte) 0 );
            msg.getMessageData().writeTo( out );
            return true;
        }
    }


    @Override
    public ByteBuffer outputBuffer(NetworkMessage msg)
    {
        ByteBuffer out = ByteBuffer.allocate( PREAMBLE_SIZE+msg.getMessageSize() );
        writeMessageTo( out, msg );
        out.rewind();

        return out;
    }


    @Override
    public NetworkMessage tryReadMessageFrom(ByteBuffer in, Object srccntxt) throws IOException
    {
        int envsize = in.getInt( in.position() );

        if( in.remaining()<envsize+Integer.BYTES )
            return null;

        in.getInt();
        in.get();

        return super.tryReadMessageFrom( in, srccntxt );
    }

}
