package reptor.distrbt.com;

import java.nio.ByteBuffer;


public interface NetworkMessage extends Message, MessageRecord
{

    short     getSender();

    // TODO: Calculating message sizes should not require a complete mapper.
    //       Messages should be able to calculate their sizes while preparing
    //       the digestions, for instance.
    int       calculateTypePlainPrefixSize(MessageMapper mapper);
    int       calculateTypeContentSize(MessageMapper mapper);

    void      prepareDigestion(MessageDigester digester);
    void      digestTypeContentTo(MessageDigestSink sink);

    void      writeTypeContentTo(ByteBuffer out, MessageMapper mapper);

}
