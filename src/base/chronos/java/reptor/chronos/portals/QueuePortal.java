package reptor.chronos.portals;

import java.util.ArrayDeque;
import java.util.function.Supplier;

import reptor.chronos.ChronosAddress;
import reptor.chronos.orphics.MessageQueueHandler;


public class QueuePortal<M> extends AbstractSingleQueuePortal<M>
{

    private final MessageQueueHandler<? super QueuePortal<M>> m_handler;


    public QueuePortal(MessageQueueHandler<? super QueuePortal<M>> handler)
    {
        super( handler.getDomainContext(), handler.getDomainContext().getDomainAddress() );

        m_handler = handler;
    }


    public QueuePortal(MessageQueueHandler<? super QueuePortal<M>> handler, ChronosAddress remdomaddr)
    {
        super( handler.getDomainContext(), remdomaddr );

        m_handler = handler;
    }


    public QueuePortal(MessageQueueHandler<? super QueuePortal<M>> handler, ChronosAddress remdomaddr, Supplier<ArrayDeque<M>> queuefac)
    {
        super( queuefac, handler.getDomainContext(), remdomaddr );

        m_handler = handler;
    }


    @Override
    public String toString()
    {
        return "PORTAL[" + m_handler.toString() + "]";
    }


    @Override
    protected void notifyHandler()
    {
        m_handler.messagesReady( this );
    }

}
