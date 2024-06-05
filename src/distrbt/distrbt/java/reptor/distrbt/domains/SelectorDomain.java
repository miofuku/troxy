package reptor.distrbt.domains;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import reptor.chronos.ChronosAddress;
import reptor.chronos.domains.AbstractGenericDomain;
import reptor.chronos.orphics.AddressName;


// TODO: Should something or even everything be initialized within the running thread?
public class SelectorDomain extends AbstractGenericDomain<SelectorDomainContext>
                            implements SelectorDomainContext
{

    private final Selector  m_selector;


    public SelectorDomain(String name)
    {
        this( new AddressName( name ) );
    }


    public SelectorDomain(ChronosAddress addr)
    {
        this( addr, new ChronosAddress[] { addr } );
    }


    public SelectorDomain(ChronosAddress addr, ChronosAddress[] remdoms)
    {
        super( addr, remdoms );

        try
        {
            m_selector = Selector.open();
        }
        catch( IOException e )
        {
            throw handleIOException( e );
        }
    }


    @Override
    public SelectorDomainContext getDomainContext()
    {
        return this;
    }


    @Override
    public SelectionKey registerChannel(ChannelHandler handler, SelectableChannel channel, int ops)
    {
        try
        {
            return channel.register( m_selector, ops, handler );
        }
        catch( ClosedChannelException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public void prepareMigrationOfRegisteredChannel(SelectionKey key)
    {

    }


    // Cancelled selection keys are not cleaned up before the next select. Transferring a channel from one handler A
    // to another B within the same domain cannot be accomplished by simply cancelling the key of A and letting
    // B create its own key. If no select had been taken place in the meanwhile, B would get an CancelledKeyException.
    // TODO: The current implementation, however, does not work if a key is transferred forth an back between domains.
    // cancel in A, register in B, register in A again could fail. Cancelling keys could become a task of the selector's
    // main loop ...
    @Override
    public SelectionKey migrateRegisteredChannel(SelectionKey key, ChannelHandler handler)
    {
        if( key.selector()==m_selector )
        {
            key.attach( handler );

            return key;
        }
        else
        {
            int ops = key.interestOps();
            key.cancel();

            return registerChannel( handler, key.channel(), ops );
        }
    }


    @Override
    protected void processEvents()
    {
        try
        {
            m_selector.selectNow();
        }
        catch( IOException e )
        {
            throw handleIOException( e );
        }

        processIOEvents();
    }


    @Override
    protected void waitForEvents(long timeout)
    {
        try
        {
            m_selector.select( timeout );
        }
        catch( IOException e )
        {
            throw handleIOException( e );
        }

        processIOEvents();
    }


    private void processIOEvents()
    {
        // Collect tasks that are able to perform I/O operations
        // TODO: Is it allowed to call domain functions such as registering timers or channels within channelReady?
        //       If so, we would have to check if there are new timers after this, if not, then why, what is the
        //       general rule?
        for( SelectionKey key : m_selector.selectedKeys() )
            ((ChannelHandler) key.attachment()).channelReady( key );
        m_selector.selectedKeys().clear();
    }


    private RuntimeException handleIOException(IOException e)
    {
        e.printStackTrace();
        return new IllegalStateException( e );
    }



    @Override
    protected void wakeup()
    {
        m_selector.wakeup();
    }

}
