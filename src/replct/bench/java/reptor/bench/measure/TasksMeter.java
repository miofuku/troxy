package reptor.bench.measure;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import reptor.measr.IntervalObserver;
import reptor.measr.aggregate.AdaptiveIntervalAggregator;
import reptor.measr.filter.FilteredIntervalMeterBuilder;
import reptor.measr.filter.TimeIntervalFilter;
import reptor.measr.generate.LongValueIntervalGenerator;
import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.meter.LongHistorySummaryMeter;
import reptor.measr.sink.SummaryStatsSink;


public class TasksMeter
{
    private class TaskObserver implements IntervalObserver
    {
        private final LongConsumer                                                           m_consumer;
        private final ArrayList<LongHistorySummaryMeter<SummaryStatsSink, SummaryStatsSink>> m_meters;


        public TaskObserver(int nquants, long intdur, long durwarm, long durrun, boolean withhis)
        {
            assert durrun > 0;

            int nrecints = Math.max( (int) (durrun / intdur), 1 );

            FilteredIntervalMeterBuilder filmetbld = new FilteredIntervalMeterBuilder();
            filmetbld.addRecordObserver( this );

            Supplier<SummaryStatsSink> sumfac = SummaryStatsSink::new;
            Supplier<SummaryStatsSink> hisfac = withhis ? SummaryStatsSink::new : null;

            m_meters = new ArrayList<>( nquants );

            for( int i = 0; i < nquants; i++ )
                m_meters.add( filmetbld.addQuantity( sumfac, hisfac, nrecints ) );

            TimeIntervalFilter filter = filmetbld.createTime( durwarm, durrun );

            IntervalObserver aggreg = new AdaptiveIntervalAggregator( filter, intdur, 10 );
            m_consumer = new LongValueIntervalGenerator( m_meters.get( 0 ), aggreg, false );
        }


        @Override
        public void intervalStarted()
        {
        }


        @Override
        public void intervalEnded()
        {
            TasksMeter.this.taskFinished();
        }


        @Override
        public void intervalCancelled()
        {
        }


        public LongConsumer getConsumer(int quantno)
        {
            return quantno == 0 ? m_consumer : m_meters.get( quantno );
        }


        public IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink> getResult(int quantno)
        {
            return m_meters.get( quantno );
        }
    }


    private final TaskObserver[] m_taskobservers;
    private final CountDownLatch m_tasksfinished;


    public TasksMeter(int nstages, int nquants, long durwarm, long durrun, long durcool, boolean withhis)
    {
        if( durrun < 0 )
        {
            m_tasksfinished = new CountDownLatch( 0 );
            m_taskobservers = null;
        }
        else
        {
            m_tasksfinished = new CountDownLatch( nstages );
            m_taskobservers = new TaskObserver[nstages];

            for( int i = 0; i < m_taskobservers.length; i++ )
                m_taskobservers[i] = new TaskObserver( nquants, TimeUnit.SECONDS.toNanos( 1 ), durwarm, durrun, withhis );
        }
    }


    public int getNumberOfTasks()
    {
        return m_taskobservers.length;
    }


    protected boolean isActive()
    {
        return m_taskobservers != null;
    }


    protected LongConsumer getTaskConsumer(int taskno, int quantno)
    {
        return m_taskobservers[taskno].getConsumer( quantno );
    }


    public boolean waitForTasks(long timeout, TimeUnit unit)
    {
        try
        {
            return m_tasksfinished.await( timeout, unit );
        }
        catch( InterruptedException e )
        {
            throw new IllegalStateException( e );
        }
    }


    protected void taskFinished()
    {
        m_tasksfinished.countDown();
    }


    public IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink> getResult(int taskno, int quantno)
    {
        return m_taskobservers[taskno].getResult( quantno );
    }
}
