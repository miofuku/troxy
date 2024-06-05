package reptor.chronos.portals;

import java.util.ArrayDeque;
import java.util.function.Supplier;

import reptor.chronos.com.Portal;


public abstract class AbstractQueuePortal<M> implements Portal<M>
{

    private ArrayDeque<M>   m_extqueue;
    private ArrayDeque<M>   m_intqueue;
    private boolean         m_domnotified = false;
    private boolean         m_isready     = false;
    private boolean         m_extready    = false;


    public AbstractQueuePortal()
    {
        m_extqueue = new ArrayDeque<>();
        m_intqueue = new ArrayDeque<>();
    }


    public AbstractQueuePortal(Supplier<ArrayDeque<M>> queuefac)
    {
        m_extqueue = queuefac.get();
        m_intqueue = queuefac.get();
    }


    protected void enqueueExternalMessage(M msg)
    {
        boolean notify;

        synchronized( this )
        {
            m_extqueue.add( msg );

            //notify      = !m_domnotify
            //m_domnotify = true;

            if( m_domnotified )
                notify = false;
            else
            {
                m_domnotified = true;
                notify = true;
            }
        }

        if( notify )
            notifyDomain();
    }


    protected abstract void notifyDomain();


    protected void enqueueInternalMessage(M msg)
    {
        m_intqueue.add( msg );

        notifyReady();
    }


    protected void externalMessagesReady()
    {
        m_extready = true;

        notifyReady();
    }


    private void notifyReady()
    {
        if( !m_isready )
        {
            m_isready = true;
            notifyHandler();
        }
    }


    protected abstract void notifyHandler();


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    protected void retrieveMessagesIfReady()
    {
        if( !m_extready )
            return;

        doRetrieveMessages();

        m_extready = false;
    }


    protected void doRetrieveMessages()
    {
        synchronized( this )
        {
            m_domnotified = false;

            if( !m_intqueue.isEmpty() )
            {
                m_intqueue.addAll( m_extqueue );
                m_extqueue.clear();
            }
            else
            {
                ArrayDeque<M> s = m_extqueue;
                m_extqueue = m_intqueue;
                m_intqueue = s;
            }
        }
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
