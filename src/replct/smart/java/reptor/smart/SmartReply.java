package reptor.smart;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import bftsmart.tom.core.messages.TOMMessage;


public class SmartReply extends Reply
{
    private final TOMMessage tommsg;


    public SmartReply(TOMMessage tommsg)
    {
        super( (short) tommsg.getSender(), (short) tommsg.getSession(), tommsg.getSequence(),
                true, ImmutableData.wrap( tommsg.getContent() ), (short) -1, null );

        this.tommsg = tommsg;
    }


    public static SmartReply readFrom(ByteBuffer buf)
    {
        TOMMessage tommsg = new TOMMessage();

        int size = buf.remaining();

        ByteArrayInputStream bais = new ByteArrayInputStream( buf.array(), buf.position(), size );
        DataInputStream dis = new DataInputStream( bais );
        try
        {
            tommsg.rExternal( dis );

            SmartReply msg = new SmartReply( tommsg );
            msg.setValid();
//            msg.setMessageSize( size );
            msg.setCertificateSize( dis.available() );
            msg.setMessageData( new DataBuffer( buf ) );

            return msg;
        }
        catch( ClassNotFoundException | IOException e )
        {
            throw new IllegalStateException( e );
        }
    }


    public TOMMessage getTOMMessage()
    {
        return tommsg;
    }
}
