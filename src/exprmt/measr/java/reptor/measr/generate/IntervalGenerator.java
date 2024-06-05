package reptor.measr.generate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import reptor.measr.IntervalObserver;


public class IntervalGenerator implements Runnable
{
    private final Object                       m_sync = new Object();
    private final long                         m_intlenms;
    private final Collection<IntervalObserver> m_listeners;

    private Thread                             m_executer;
    private int                                m_intcnt;
    private volatile boolean                   m_iscancelled;


    public IntervalGenerator(long intdur, int intcnt, IntervalObserver... listeners)
    {
        this( intdur, intcnt, Arrays.asList( listeners ) );
    }


    public IntervalGenerator(long intdur, int intcnt, Collection<IntervalObserver> listeners)
    {
        m_intlenms = TimeUnit.NANOSECONDS.toMillis( intdur );
        m_intcnt = intcnt;
        m_listeners = new ArrayList<>( listeners );
    }


    @Override
    public void run()
    {
        if( m_iscancelled )
            return;

        synchronized( m_sync )
        {
            if( m_executer != null )
                throw new IllegalStateException( "Generator was already executed." );

            m_executer = Thread.currentThread();
        }

        while( !m_iscancelled && m_intcnt != 0 )
        {
            for( IntervalObserver l : m_listeners )
                l.intervalStarted();

            try
            {
                Thread.sleep( m_intlenms );
            }
            catch( InterruptedException e )
            {
            }

            if( m_intcnt > 0 )
                m_intcnt--;
        }

        if( !m_iscancelled )
        {
            for( IntervalObserver l : m_listeners )
                l.intervalEnded();
        }
    }


    public void cancel()
    {
        if( m_iscancelled )
            return;

        m_iscancelled = true;

        synchronized( m_sync )
        {
            if( m_executer != null )
                m_executer.interrupt();
        }
    }


    public boolean isCancelled()
    {
        return m_iscancelled;
    }
}
