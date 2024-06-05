package reptor.chronos.domains;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomain;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.chronos.portals.AbstractQueuePortal;
import reptor.chronos.time.TimeKeeper;


// TODO: Should something or even everything be initialized within the running thread?
public abstract class AbstractGenericDomain<D extends ChronosDomainContext>
        implements GenericDomain<D>, ChronosDomainContext, SchedulerContext<D>, ChronosAddress
{

    private static final ThreadLocal<ChronosDomain> m_thisdom = new ThreadLocal<>();

    private final ChronosAddress       m_addr;
    private final ExitPortal           m_exitportal   = new ExitPortal();
    private final ReadyPortals         m_readyportals;
    private final TimeKeeper           m_time         = new TimeKeeper();

    private ChronosTask                m_task;
    private boolean                    m_isrunning    = false;


    public AbstractGenericDomain(ChronosAddress addr, ChronosAddress[] remdoms)
    {
        m_addr = Objects.requireNonNull( addr );
        m_readyportals = new ReadyPortals();
    }


    @Override
    public String toString()
    {
        return m_addr.toString();
    }


    @Override
    public ChronosAddress getAddress()
    {
        return m_addr;
    }


    @Override
    public ChronosAddress getDomainAddress()
    {
        return m_addr;
    }


    @Override
    public SchedulerContext<D> getContext()
    {
        return this;
    }


    @Override
    public void registerTask(ChronosTask task)
    {
        Preconditions.checkState( m_task==null );

        m_task = Objects.requireNonNull( task );
    }


    @Override
    public List<ChronosTask> listSubordinates()
    {
        return Collections.singletonList( m_task );
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
    public void initContext()
    {
        m_thisdom.set( this );
    }


    @Override
    public boolean checkDomain()
    {
        return m_thisdom.get()==this;
    }


    @Override
    public ChronosDomain getCurrentDomain()
    {
        return m_thisdom.get();
    }


    private class ExitPortal extends AbstractQueuePortal<Object>
    {
        @Override
        public PushMessageSink<Object> createChannel(ChronosAddress origin)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "PEXIT[" + AbstractGenericDomain.this + "]";
        }

        @Override
        public void enqueueMessage(Object msg)
        {
            notifyDomain();
        }

        @Override
        protected void notifyDomain()
        {
            m_readyportals.createChannel( m_addr ).enqueueMessage( this );
        }

        @Override
        public void messagesReady()
        {
            m_isrunning = false;
        }

        @Override
        public void retrieveMessages()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void notifyHandler()
        {
            throw new UnsupportedOperationException();
        }
    }


    // TODO: Shutdown with timer.
    @Override
    public void shutdown()
    {
        m_exitportal.enqueueMessage( null );
    }


//    private class ReadyMultiPortals extends AbstractMultiQueuePortal<Portal<?>>
//    {
//        public ReadyMultiPortals(ChronosAddress[] remdoms)
//        {
//            super( AbstractGenericDomain.this, remdoms );
//        }
//
//        @Override
//        protected Portal<Portal<?>> createRemotePortal(int portno, ChronosAddress remdom)
//        {
//            return new ReadyPortals();
//        }
//
//        @Override
//        public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
//        {
//            return createRemoteChannel( m_addr );
//        }
//
//        @Override
//        public void enqueueMessage(Portal<?> msg)
//        {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        protected void notifyHandler()
//        {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public void retrieveMessages()
//        {
//            doRetrieveMessages();
//        }
//
//        @Override
//        public Portal<?> poll()
//        {
//            return pollPortals();
//        }
//    }


    private class ReadyPortals extends AbstractQueuePortal<Portal<?>>
    {
        @Override
        public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
        {
            return this;
        }

        @Override
        public String toString()
        {
            return "PREADY[" + AbstractGenericDomain.this + "]";
        }

        @Override
        public void enqueueMessage(Portal<?> msg)
        {
            enqueueExternalMessage( msg );
        }

        @Override
        protected void notifyDomain()
        {
            wakeup();
        }

        @Override
        public void messagesReady()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retrieveMessages()
        {
            doRetrieveMessages();
        }

        @Override
        protected void notifyHandler()
        {
            throw new UnsupportedOperationException();
        }
    }


    @Override
    public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
    {
        return m_readyportals.createChannel( origin );
    }


    protected abstract void wakeup();


    @Override
    public void taskReady(ChronosTask task)
    {

    }


    @Override
    public void run()
    {
        boolean haswork = m_task.isReady();

        m_isrunning = true;

        while( true )
        {
            // Block until at least one task becomes runnable or a timer is potentially due.
            boolean hastimers = m_time.hasTimers();

            if( haswork )
                processEvents();
            else if( !hastimers )
                waitForEvents( 0 );
            else
            {
                long timeout = m_time.nextTimeout();

                if( timeout<=0 )
                    processEvents();
                else
                    waitForEvents( timeout );
            }

            if( hastimers )
                m_time.processTimers();

            processPortals();

            if( !m_isrunning )
                break;

            haswork = !m_task.execute();
        }
    }


    protected abstract void processEvents();

    // timeout==0 -> wait indefinitely
    protected abstract void waitForEvents(long timeout);


    private void processPortals()
    {
        m_readyportals.retrieveMessages();

        Portal<?> portal;
        while( ( portal = m_readyportals.poll() )!=null )
            portal.messagesReady();
    }

}
