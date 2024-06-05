package reptor.replct.invoke;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomain;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.chronos.time.TimeKeeper;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.invoke.bft.ProphecySketcher;
import reptor.replct.service.ServiceCommand;


public class BlockingInvocationHandler implements SchedulerContext<SelectorDomainContext>, SelectorDomainContext,
                                                  ChronosAddress
{

    private final Selector                  m_selector;
    private final TimeKeeper                m_time;
    private final InvocationClientHandler   m_invhandler;


    public BlockingInvocationHandler(short clino, InvocationClient invcli, ProphecySketcher sketcher) throws IOException
    {
        m_selector   = Selector.open();
        m_time       = new TimeKeeper();
        m_invhandler = invcli.createInvocationProvider().createInvocationHandler( this, clino, sketcher );
    }


    public ServiceCommand invokeService(ServiceCommand command) throws IOException
    {
        m_invhandler.startInvocation( command );

        while( true )
        {
            while( m_invhandler.isReady() )
                m_invhandler.execute();

            if( m_invhandler.pollResult()!=null )
                break;

            boolean hastimers = m_time.hasTimers();
            long    timeout   = translateTimeout( hastimers, m_time );

            if( timeout<0 )
                m_selector.selectNow();
            else
                m_selector.select( timeout );

            for( SelectionKey key : m_selector.selectedKeys() )
                ((ChannelHandler) key.attachment()).channelReady( key );
            m_selector.selectedKeys().clear();

            if( hastimers )
                m_time.processTimers();
        }

        return command;
    }


    private long translateTimeout(boolean hastimers, TimeKeeper time)
    {
        if( !hastimers )
            return 0;
        else
        {
            long timeout = time.nextTimeout();

            return timeout<=0 ? -1 : timeout;
        }
    }


    @Override
    public SelectorDomainContext getDomainContext()
    {
        return this;
    }


    @Override
    public ChronosAddress getDomainAddress()
    {
        return this;
    }


    @Override
    public boolean checkDomain()
    {
        return true;
    }


    @Override
    public ChronosDomain getCurrentDomain()
    {
        return null;
    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }


    @Override
    public long time()
    {
        return System.nanoTime();
    }


    @Override
    public TimeKey registerTimer(TimerHandler handler)
    {
        return m_time.registerTimer( handler );
    }


    @Override
    public SelectionKey registerChannel(ChannelHandler worker, SelectableChannel channel, int ops)
    {
        try
        {
            return channel.register( m_selector, ops, worker );
        }
        catch( ClosedChannelException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    @Override
    public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void prepareMigrationOfRegisteredChannel(SelectionKey key)
    {

    }


    @Override
    public SelectionKey migrateRegisteredChannel(SelectionKey key, ChannelHandler handler)
    {
        if( key.selector()!=m_selector )
            throw new UnsupportedOperationException();

        key.attach( handler );

        return key;
    }

}
