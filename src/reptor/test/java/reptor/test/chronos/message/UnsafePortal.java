package reptor.test.chronos.message;

import java.util.ArrayDeque;
import java.util.Objects;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.chronos.portals.QueuePortal;


public class UnsafePortal<M> implements Portal<M>
{

    private final MessageQueueHandler<? super UnsafePortal<M>> m_handler;

    private final ArrayDeque<M> m_intqueue = new ArrayDeque<>();
    private boolean             m_isready  = false;


    public UnsafePortal(MessageQueueHandler<? super UnsafePortal<M>> handler)
    {
        m_handler = Objects.requireNonNull( handler );
    }


    @Override
    public PushMessageSink<M> createChannel(ChronosAddress origin)
    {
        return this;
    }


    @Override
    public void enqueueMessage(M msg)
    {
        m_intqueue.add( msg );

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
    public void messagesReady()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    @Override
    public void retrieveMessages()
    {
    }


    @Override
    public M poll()
    {
        M msg = m_intqueue.poll();

        if( msg==null )
            m_isready = false;

        return msg;
    }

}
