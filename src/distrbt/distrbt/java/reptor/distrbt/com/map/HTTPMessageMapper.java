package reptor.distrbt.com.map;

import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.distrbt.com.TypedMessageDeserializer;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.jlib.hash.HashAlgorithm;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by bli on 20.07.17.
 */
public class HTTPMessageMapper extends BasicMessageMapper
{
    private final CommonHeaderMapper            m_hdrmapper;

    public HTTPMessageMapper(NetworkMessageRegistry msgreg, BasicMessageDigestionStrategy.Variant digvariant, HashAlgorithm digalgo)
    {
        super(msgreg, digvariant, digalgo);

        m_hdrmapper = new SizeFirstHeaderMapper( msgreg );
    }

    @Override
    public NetworkMessage tryReadMessageFrom(ByteBuffer in, Object srccntxt) throws IOException
    {
        // Add common header to http request
        tryAddCommonHeader(in);

        DeserializationContext cntxt = (DeserializationContext) srccntxt;

        if( cntxt.getDeserializer()==null && !m_hdrmapper.tryReadCommonHeader( in, cntxt ) )
            return null;

        assert !cntxt.isMessageComplete();

        if( !copyBuffer( in, cntxt ) )
            return null;

        TypedMessageDeserializer deserializer = cntxt.getDeserializer();
        Data   msgdata  = cntxt.getBuffer();
        int    hdrsize  = cntxt.getHeaderSize();
        Object msgcntxt = cntxt.createMessageContext();

        NetworkMessage msg = deserializeMessage( deserializer, msgdata, hdrsize, msgdata.byteBuffer(), msgcntxt );

        cntxt.clear();

        return msg;
    }

    private void tryAddCommonHeader(ByteBuffer in)
    {
        if (in.remaining()>0)
        {
            byte[] tmp = new byte[in.remaining()];
            in.get(tmp);
            String method = new String(tmp,0,3);

            if (method.equalsIgnoreCase("GET")) // HTTP GET request
            {
                in.clear();
                byte[] bytes = new byte[tmp.length+100];
                in.wrap(bytes);
                in.putInt(tmp.length+18); // message length
                in.put((byte)0); // magic is 0 for request
                in.putShort((short)3); // sender, can be adjusted later
                in.putLong((long)0); // sequence number, can be adjusted later
                in.put((byte) 0x02); // read-only flag
                in.putShort((short)tmp.length); // content length
                in.put(tmp);
                in.flip();
            }
            else if (method.equalsIgnoreCase("POS")) // POST request
            {
                in.clear();
                byte[] bytes = new byte[tmp.length+100];
                in.wrap(bytes);
                in.putInt(tmp.length+18); // message length
                in.put((byte)0); // magic is 0 for request
                in.putShort((short)3); // sender, can be adjusted later
                in.putLong((long)0); // sequence number, can be adjusted later
                in.put((byte) 0x00); // read-only flag
                in.putShort((short)tmp.length); // content length
                in.put(tmp);
                in.flip();
            }
            else
                in.rewind();
        }
    }

    @Override
    public void serializeMessage(NetworkMessage msg)
    {


        if (msg.getMessageTypeID() != 16777220)
        {
            serializeMessage(msg, null);
        }
        else
        {
            int cntsize;

            if( msg.hasContentSize() )
                cntsize = msg.getContentSize();
            else
                cntsize = initReplyContentSizes( msg );

            int certsize = 0;
            msg.setCertificateSize( certsize );

            DataBuffer msgbuffer = new DataBuffer( cntsize+certsize );
            msg.setMessageData( msgbuffer );

            ByteBuffer out = msgbuffer.byteBuffer();

            writeReplyMessageContentTo( out, msg );
        }
    }

    private int initReplyContentSizes(NetworkMessage msg)
    {
        int cntsize = msg.calculateTypeContentSize(this);
        msg.setContentSizes(0, cntsize);

        return cntsize;
    }

    private void writeReplyMessageContentTo(ByteBuffer out, NetworkMessage msg)
    {
        out.limit(0);
        msg.writeTypeContentTo(out, this);
    }

}
