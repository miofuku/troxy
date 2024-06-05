package reptor.chronos.portals;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.MessageQueueHandler;


public class ConcurrentPortal<M> implements Portal<M>
{

    private final MessageQueueHandler<? super ConcurrentPortal<M>>  m_handler;
    private final PushMessageSink<Portal<?>>                        m_portalready;

    private final ConcurrentLinkedQueue<M> m_extqueue    = new ConcurrentLinkedQueue<>();
    private final ExternalSink             m_extsink     = new ExternalSink();
    private AtomicBoolean                  m_domnotified = new AtomicBoolean( false );
    private boolean                        m_isready     = false;


    public ConcurrentPortal(MessageQueueHandler<? super ConcurrentPortal<M>> handler)
    {
        m_handler     = Objects.requireNonNull( handler );
        m_portalready = handler.getDomainContext().createChannel( handler.getDomainContext().getDomainAddress() );
    }


    @Override
    public PushMessageSink<M> createChannel(ChronosAddress origin)
    {
        return origin.equals( m_handler.getDomainContext().getDomainAddress() ) ? this : m_extsink;
    }


    private class ExternalSink implements PushMessageSink<M>
    {
        @Override
        public void enqueueMessage(M msg)
        {
            enqueueExternalMessage( msg );
        }
    }


    private void enqueueExternalMessage(M msg)
    {
        m_extqueue.add( msg );

        if( m_domnotified.compareAndSet( false, true ) )
            m_portalready.equals( this );
    }


    @Override
    public void enqueueMessage(M msg)
    {
        m_extqueue.add( msg );

        notifyReady();
    }


    @Override
    public void messagesReady()
    {
        notifyReady();
    }


    private void notifyReady()
    {
        if( !m_isready )
        {
            m_isready = true;
            m_handler.messagesReady( this );
        }
    }


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    @Override
    public void retrieveMessages()
    {
        m_domnotified.set( false );
    }


    @Override
    public M poll()
    {
        M msg = m_extqueue.poll();

        if( msg==null )
            m_isready = false;

        return msg;
    }

}
