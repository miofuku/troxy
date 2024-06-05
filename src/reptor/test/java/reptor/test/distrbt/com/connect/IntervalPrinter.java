package reptor.test.distrbt.com.connect;

import java.util.function.LongConsumer;

import reptor.bench.IntervalResultFormatter;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.chronos.orphics.AbstractTask;
import reptor.measr.sink.LongStatsSink;


public class IntervalPrinter extends AbstractTask
                             implements TimerHandler, LongConsumer
{

    private final SchedulerContext<?>   m_cntxt;
    private final ChronosDomainContext  m_domcntxt;

    private final int                   m_intlen;
    private TimeKey                     m_timekey;

    private final LongStatsSink         m_intsum = new LongStatsSink();
    // TODO: DomainStopwatch
    private long                        m_intts;
    private int                         m_intno  = 0;


    public IntervalPrinter(SchedulerContext<?> cntxt, int intlen)
    {
        m_cntxt    = cntxt;
        m_domcntxt = cntxt.getDomainContext();
        m_intlen   = intlen;
    }


    @Override
    protected SchedulerContext<?> master()
    {
        return m_cntxt;
    }


    private void schedule()
    {
        m_timekey.schedule( m_intlen );
    }


    private long time()
    {
        return m_domcntxt.time();
    }


    public void start()
    {
        m_timekey = master().getDomainContext().registerTimer( this );
        schedule();

        m_intts = time();
    }


    @Override
    public void timeElapsed(TimeKey key)
    {
        notifyReady();
    }


    @Override
    public void accept(long value)
    {
        m_intsum.accept( value );
    }


    @Override
    public boolean execute()
    {
        if( !isReady() )
            return true;

        long curts = time();

        IntervalResultFormatter.printInterval( null, ++m_intno, curts-m_intts, m_intsum );

        m_intsum.reset();
        m_intts = curts;

        schedule();

        return isDone( true );
    }

}