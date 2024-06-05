package reptor.test.chronos.message;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import reptor.chronos.ChronosAddress;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.domains.AbstractGenericDomain;


public class TestDomain extends AbstractGenericDomain<ChronosDomainContext>
{

    private enum Mode
    {
        Sync,
        Condition,
        Guarded,
        Selector,
        Sleep;
    }

    private final Mode m_mode = Mode.Selector;

    private final ReentrantLock m_lock   = new ReentrantLock();
    private final Condition     m_signal = m_lock.newCondition();
    private boolean             m_usig   = false;

    private AtomicBoolean       m_signaled = new AtomicBoolean( false );

    private Selector m_selector;
    private Thread   m_thread;


    public TestDomain(ChronosAddress addr, ChronosAddress[] remdoms)
    {
        super( addr, remdoms );

        try
        {
            m_selector = Selector.open();
        }
        catch( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public void initContext()
    {
        super.initContext();

        m_thread = Thread.currentThread();
    }


    @Override
    public ChronosDomainContext getDomainContext()
    {
        return this;
    }


    @Override
    protected void wakeup()
    {
        switch( m_mode )
        {
        case Sync:
            synchronized( this )
            {
                if( !m_usig )
                {
                    m_usig = true;
                    notify();
                }
            }
            break;
        case Condition:
            m_lock.lock();
            m_usig = true;
            m_signal.signal();
            m_lock.unlock();
            break;
        case Guarded:
            if( !m_signaled.getAndSet( true ) )
            {
                m_lock.lock();
                m_usig = true;
                m_signal.signal();
                m_lock.unlock();
            }
            break;
        case Selector:
            m_selector.wakeup();
            break;
        case Sleep:
            m_thread.interrupt();
            break;
        }
    }

    @Override
    protected void processEvents()
    {
    }


    @Override
    protected void waitForEvents(long timeout)
    {
        try
        {
            switch( m_mode )
            {
            case Sync:
                synchronized( this )
                {
                    if( !m_usig )
                    {
                        if( timeout==0 )
                            wait();
                        else
                            wait( timeout );
                    }

                    m_usig = false;
                }
                break;
            case Condition:
                  m_lock.lock();

                  if( !m_usig )
                  {
                      if( timeout==0 )
                          m_signal.await();
                      else
                          m_signal.awaitNanos( timeout*1000000L );
                  }
                  m_usig = false;

                  m_lock.unlock();
                break;
            case Guarded:
                if( !m_signaled.getAndSet( false ) )
                {
                    m_lock.lock();

                    if( !m_usig )
                    {
                        if( timeout==0 )
                            m_signal.await();
                        else
                            m_signal.awaitNanos( timeout*1000000L );
                    }
                    m_signaled.set( false );
                    m_usig = false;

                    m_lock.unlock();
                }
                break;
            case Selector:
                m_selector.select( timeout );
                break;
            case Sleep:
                try
                {
                    if( timeout==0 )
                        Thread.sleep( Long.MAX_VALUE );
                    else
                        Thread.sleep( timeout );
                }
                catch( InterruptedException e )
                {

                }
                break;
            }
        }
        catch( Exception e )
        {
            throw new IllegalStateException( e );
        }
    }

}