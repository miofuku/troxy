package reptor.distrbt.com;

import java.io.IOException;
import java.nio.ByteBuffer;


public abstract class AbstractNetworkMessage extends AbstractMessageRecord implements NetworkMessage
{

    protected AbstractNetworkMessage()
    {
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
    }


    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        return 0;
    }


    @Override
    public int calculateTypeContentSize(MessageMapper mapper)
    {
        return 0;
    }


    // TODO: Confirm read values and throw an IOException in the case of an error.
    protected AbstractNetworkMessage(ByteBuffer in) throws IOException
    {

    }


    @Override
    public void prepareDigestion(MessageDigester digester)
    {

    }


    @Override
    public void digestTypeContentTo(MessageDigestSink sink)
    {

    }


    @Override
    public int getMessageTypeID()
    {
        return getTypeID();
    }


    @Override
    public NetworkMessage getMessage()
    {
        return this;
    }

}
