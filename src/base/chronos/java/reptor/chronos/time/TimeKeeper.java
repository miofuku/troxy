package reptor.chronos.time;

import java.util.PriorityQueue;

import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;

// TODO: java.time.*
public class TimeKeeper
{

    private PriorityQueue<LocalTimeKey> m_timers = new PriorityQueue<>();


    public TimeKey registerTimer(TimerHandler handler)
    {
        return new LocalTimeKey( handler );
    }


    public boolean hasTimers()
    {
        return !m_timers.isEmpty();
    }


    // Must only be called when hasTimer() returns true. The returned value can be positive, negative, or zero,
    // where value<=0 means that processTimers() should be invoked as soon as possible.
    // Note that the returned timeout can stem from a cancelled timer since timers are only removed within
    // processTimers().
    public long nextTimeout()
    {
        return m_timers.peek().getNextExecutionTime() - timerTime();
    }


    // Returns if everything is done, which is the case when there are no active timers left.
    public boolean processTimers()
    {
        LocalTimeKey nexttimer = m_timers.peek();

        if( nexttimer==null )
            return true;

        long curtime = timerTime();

        while( nexttimer!=null && nexttimer.getNextExecutionTime()<=curtime )
        {
            m_timers.poll();

            nexttimer.trigger();

            nexttimer = m_timers.peek();
        }

        return nexttimer==null;
    }


    private long timerTime()
    {
        return System.currentTimeMillis();
    }


    private class LocalTimeKey implements TimeKey, Comparable<LocalTimeKey>
    {
        private TimerHandler m_handler;
        private long         m_nexttime;

        private LocalTimeKey(TimerHandler handler)
        {
            m_handler  = handler;
            m_nexttime = -1;
        }

        @Override
        public void schedule(long delay)
        {
            assert m_nexttime==-1;

            m_nexttime = timerTime() + delay;

            m_timers.add( this );
        }

        @Override
        public void clear()
        {
            if( m_nexttime==-1 )
                return;

            m_timers.remove( this );
            m_nexttime = -1;
        }

        @Override
        public TimerHandler handler()
        {
            return m_handler;
        }

        public void trigger()
        {
            m_nexttime = -1;
            m_handler.timeElapsed( this );
        }

        public long getNextExecutionTime()
        {
            return m_nexttime;
        }

        @Override
        public int compareTo(LocalTimeKey o)
        {
            return Long.compare( m_nexttime, o.m_nexttime );
        }
    }

}
