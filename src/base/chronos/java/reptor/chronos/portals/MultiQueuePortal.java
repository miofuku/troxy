package reptor.chronos.portals;

import java.util.ArrayDeque;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.MessageQueueHandler;


public class MultiQueuePortal<M> extends AbstractMultiQueuePortal<M>
{

    private final MessageQueueHandler<? super Portal<M>> m_handler;

    private final ArrayDeque<M> m_intqueue;


    public MultiQueuePortal(MessageQueueHandler<? super Portal<M>> handler, ChronosAddress[] remdoms)
    {
        super( handler.getDomainContext(), remdoms );

        m_handler  = handler;
        m_intqueue = new ArrayDeque<>();
    }


    @Override
    protected Portal<M> createRemotePortal(int portno, ChronosAddress remdom)
    {
        return new NumberedQueuePortal<>( this, portno, remdom );
    }


    @Override
    public String toString()
    {
        return "PORTAL[" + m_handler.toString() + "]";
    }


    @Override
    public PushMessageSink<M> createChannel(ChronosAddress origin)
    {
        return origin.equals( m_handler.getDomainContext().getDomainAddress() ) ? this : createRemoteChannel( origin );
    }


    @Override
    public void enqueueMessage(M msg)
    {
        m_intqueue.add( msg );

        messagesReady();
    }


    @Override
    protected void notifyHandler()
    {
        m_handler.messagesReady( this );
    }


    @Override
    public void retrieveMessages()
    {
        retrieveMessagesIfReady();
    }


    @Override
    public M poll()
    {
        if( !m_intqueue.isEmpty() )
            return m_intqueue.poll();
        else if( isPortalReady() )
            return pollPortals();
        else
        {
            clearReady();
            return null;
        }
    }

}