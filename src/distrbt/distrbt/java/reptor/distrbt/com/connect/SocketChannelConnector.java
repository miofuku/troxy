package reptor.distrbt.com.connect;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractTask;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;


public class SocketChannelConnector extends AbstractTask implements ChannelHandler
{

    private final SchedulerContext<? extends SelectorDomainContext> m_master;

    private SocketChannel           m_channel = null;
    private SelectionKey            m_selkey  = null;
    private IOException             m_error   = null;


    public SocketChannelConnector(SchedulerContext<? extends SelectorDomainContext> master)
    {
        m_master = master;
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    protected SelectorDomainContext domainContext()
    {
        return m_master.getDomainContext();
    }


    public boolean isUsed()
    {
        return m_channel!=null;
    }


    public boolean isOpen()
    {
        return isUsed() && m_channel.isOpen();
    }


    public boolean isConnected()
    {
        return isUsed() && m_channel.isConnected();
    }


    public SocketChannel getChannel()
    {
        return m_channel;
    }


    public SelectionKey getSelectionKey()
    {
        return m_selkey;
    }


    public boolean hasError()
    {
        return m_error!=null;
    }


    public IOException getError()
    {
        return m_error;
    }


    public boolean connect(SocketAddress remaddr) throws IOException
    {
        if( isUsed() )
            throw new IllegalStateException();

        m_channel = SocketChannel.open();
        m_channel.configureBlocking( false );

        if( m_channel.connect( remaddr ) )
            return true;
        else
        {
            m_selkey = domainContext().registerChannel( this, m_channel, 0 );
            m_selkey.interestOps( SelectionKey.OP_CONNECT );
            return false;
        }
    }


    public void clear(boolean close)
    {
        if( !isUsed() )
            return;

        if( close )
        {
            if( m_selkey!=null )
                m_selkey.cancel();

            try
            {
                m_channel.close();
            }
            catch( IOException e )
            {
            }
        }

        m_selkey  = null;
        m_channel = null;
        m_error   = null;
    }


    @Override
    public void channelReady(SelectionKey key)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        try
        {
            if( m_channel.finishConnect() && m_selkey!=null )
            {
                m_selkey.interestOps( 0 );
                domainContext().prepareMigrationOfRegisteredChannel( m_selkey );
            }
        }
        catch( IOException e )
        {
            m_error = e;
        }

        return isDone( true );
    }

}
