package reptor.chronos.portals;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.MessageQueueHandler;


public class NonbockingQueuePortal<M> extends AbstractNonblockingQueuePortal<M>
                                      implements DomainEndpoint<PushMessageSink<M>>
{

    private final MessageQueueHandler<? super Portal<M>>    m_handler;
    private final PushMessageSink<Portal<?>>                m_portalready;

    private final ExternalChannel                           m_extchannel;


    public NonbockingQueuePortal(MessageQueueHandler<? super Portal<M>> handler)
    {
        m_handler     = handler;
        m_portalready = handler.getDomainContext().createChannel( handler.getDomainContext().getDomainAddress() );
        m_extchannel  = new ExternalChannel();
    }


    @Override
    public String toString()
    {
        return "PORTAL[" + m_handler.toString() + "]";
    }


    @Override
    public PushMessageSink<M> createChannel(ChronosAddress origin)
    {
        return origin.equals( m_handler.getDomainContext().getDomainAddress() ) ? this : m_extchannel;
    }


    private class ExternalChannel implements PushMessageSink<M>
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


    @Override
    protected void notifyHandler()
    {
        m_handler.messagesReady( this );
    }

}