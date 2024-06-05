package reptor.chronos.portals;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import reptor.chronos.com.Portal;


public abstract class AbstractNonblockingQueuePortal<M> implements Portal<M>
{

    private AtomicReference<ConcurrentLinkedQueue<M>>   m_extinqueue;
    private ConcurrentLinkedQueue<M>                    m_extoutqueue;
    private ArrayDeque<M>                               m_intqueue;
    private AtomicBoolean                               m_domnotified = new AtomicBoolean();
    private boolean                                     m_isready     = false;
    private boolean                                     m_extready    = false;


    public AbstractNonblockingQueuePortal()
    {
        m_extinqueue  = new AtomicReference<>( new ConcurrentLinkedQueue<>() );
        m_extoutqueue = new ConcurrentLinkedQueue<>();
        m_intqueue    = new ArrayDeque<>();
    }


    protected void enqueueExternalMessage(M msg)
    {
        // A getAndSet in retrieveMessages could occur just between the get() and the add().
        // That is, the add() could be invoked on the then outqueue.
        m_extinqueue.get().add( msg );

        if( m_domnotified.compareAndSet( false, true ) )
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


    protected abstract void notifyHandler();


    public void notifyReady()
    {
        if( !m_isready )
        {
            m_isready = true;
            notifyHandler();
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
        if( !m_extready )
            return;

        m_domnotified.set( false );

        m_extoutqueue = m_extinqueue.getAndSet( m_extoutqueue );

        M msg;

        // We cannot use addAll() and clear(), see above.
        while( ( msg = m_extoutqueue.poll() )!=null )
            m_intqueue.add( msg );

        m_extready = false;
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
