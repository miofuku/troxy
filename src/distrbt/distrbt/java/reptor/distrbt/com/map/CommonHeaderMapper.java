package reptor.distrbt.com.map;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.TypedMessageDeserializer;


public interface CommonHeaderMapper
{

    interface CommonHeaderHolder
    {
        void setHeaderInformation(TypedMessageDeserializer deserializer, int msgsize, int hdrsize);
    }


    void writeCommonHeaderTo(ByteBuffer out, NetworkMessage msg);

    int calculateCommonHeaderSize(NetworkMessage msg);

    int getMaximumHeaderSize();

    boolean tryReadCommonHeader(ByteBuffer in, CommonHeaderHolder holder) throws IOException;

}
