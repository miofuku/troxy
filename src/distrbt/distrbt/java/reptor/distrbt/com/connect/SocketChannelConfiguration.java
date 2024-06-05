package reptor.distrbt.com.connect;

import java.io.IOException;
import java.nio.channels.SocketChannel;


public interface SocketChannelConfiguration
{
    void    configureChannel(SocketChannel channel) throws IOException;
}
