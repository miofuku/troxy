package reptor.distrbt.domains;

import java.nio.channels.SelectionKey;

import reptor.chronos.Asynchronous;
import reptor.chronos.Orphic;


public interface ChannelHandler extends Orphic
{
    @Asynchronous
    void channelReady(SelectionKey key);
}
