package reptor.distrbt.com;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface TypedMessageDeserializer
{
    NetworkMessage readMessageFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException;
}