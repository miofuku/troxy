package reptor.chronos.portals;

import java.util.ArrayDeque;
import java.util.function.Supplier;

import reptor.chronos.ChronosAddress;
import reptor.chronos.orphics.MessageQueueHandler;


public class NumberedQueuePortal<M> extends AbstractSingleQueuePortal<M>
{

    private final MessageQueueHandler<? super NumberedQueuePortal<M>>   m_handler;
    private final int                                                   m_portno;


    public NumberedQueuePortal(MessageQueueHandler<? super NumberedQueuePortal<M>> handler, int portno)
    {
        super( handler.getDomainContext(), handler.getDomainContext().getDomainAddress() );

        m_handler = handler;
        m_portno  = portno;
    }


    public NumberedQueuePortal(MessageQueueHandler<? super NumberedQueuePortal<M>> handler, int portno, ChronosAddress remdomaddr)
    {
        super( handler.getDomainContext(), remdomaddr );

        m_handler = handler;
        m_portno  = portno;
    }


    public NumberedQueuePortal(MessageQueueHandler<? super NumberedQueuePortal<M>> handler, int portno, ChronosAddress remdomaddr,
                               Supplier<ArrayDeque<M>> queuefac)
    {
        super( queuefac, handler.getDomainContext(), remdomaddr );

        m_handler = handler;
        m_portno  = portno;
    }


    @Override
    public String toString()
    {
        return "PORTAL[" + m_portno + "][" + m_handler.toString() + "]";
    }


    public int getNumber()
    {
        return m_portno;
    }


    @Override
    protected void notifyHandler()
    {
        m_handler.messagesReady( this );
    }

}