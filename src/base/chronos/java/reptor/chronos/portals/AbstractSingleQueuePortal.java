package reptor.chronos.portals;

import java.util.ArrayDeque;
import java.util.function.Supplier;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;


public abstract class AbstractSingleQueuePortal<M> extends AbstractQueuePortal<M>
{

    private final ChronosDomainContext          m_domcntxt;
    private final PushMessageSink<Portal<?>>    m_portalready;

    private final ExternalSink                  m_extsink = new ExternalSink();


    public AbstractSingleQueuePortal(ChronosDomainContext domcntxt, ChronosAddress remdomaddr)
    {
        m_domcntxt    = domcntxt;
        m_portalready = domcntxt.createChannel( remdomaddr );
    }


    public AbstractSingleQueuePortal(Supplier<ArrayDeque<M>> queuefac, ChronosDomainContext domcntxt, ChronosAddress remdomaddr)
    {
        super( queuefac );

        m_domcntxt    = domcntxt;
        m_portalready = domcntxt.createChannel( remdomaddr );
    }


    @Override
    public PushMessageSink<M> createChannel(ChronosAddress origin)
    {
        return origin.equals( m_domcntxt.getDomainAddress() ) ? this : m_extsink;
    }


    @Override
    public void retrieveMessages()
    {
        retrieveMessagesIfReady();
    }


    private class ExternalSink implements PushMessageSink<M>
    {
        @Override
        public void enqueueMessage(M msg)
        {
            enqueueExternalMessage( msg );
        }
    }


    @Override
    protected void notifyDomain()
    {
        m_portalready.enqueueMessage( this );
    }


    @Override
    public void messagesReady()
    {
        externalMessagesReady();
    }


    @Override
    public void enqueueMessage(M msg)
    {
        enqueueInternalMessage( msg );
    }

}
