package reptor.distrbt.domains;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import reptor.chronos.context.ChronosDomainContext;


public interface SelectorDomainContext extends ChronosDomainContext
{
    SelectionKey registerChannel(ChannelHandler handler, SelectableChannel channel, int ops);
    void         prepareMigrationOfRegisteredChannel(SelectionKey key);
    SelectionKey migrateRegisteredChannel(SelectionKey key, ChannelHandler handler);
}
