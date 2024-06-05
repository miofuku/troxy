package reptor.bench.measure;

import java.util.function.LongConsumer;

import reptor.distrbt.com.Message;
import reptor.measr.meter.Stopwatch;
import reptor.replct.agree.order.OrderExtensions;
import reptor.replct.agree.order.OrderExtensions.OrderInstanceObserver;


public class ProtocolInstanceMeter extends TasksMeter implements OrderExtensions.ProtocolInstanceExtension
{
    private static class InstanceObserver implements OrderExtensions.OrderInstanceObserver
    {
        private final Stopwatch    m_stopwatch = new Stopwatch();
        private final LongConsumer m_consumer;


        public InstanceObserver(LongConsumer consumer)
        {
            m_consumer = consumer;
        }


        @Override
        public void messageFetched(Message msg)
        {
            if( msg != null && !m_stopwatch.isRecording() )
                m_stopwatch.start();
        }


        @Override
        public void proposalFetched(Message proposal)
        {
            if( proposal != null && !m_stopwatch.isRecording() )
                m_stopwatch.start();
        }


        @Override
        public void instanceInitialized(long instid, int viewid)
        {
            if( m_stopwatch.isRecording() )
                m_stopwatch.stopAndReset();
        }


        @Override
        public void instanceCompleted(Message result)
        {
            assert m_stopwatch.isRecording() && result != null;

            m_consumer.accept( m_stopwatch.stopAndReset() );
        }
    }


    public ProtocolInstanceMeter(int nstages, long durwarm, long durrun, long durcool, boolean withhis)
    {
        super( nstages, 1, durwarm, durrun, durcool, withhis );
    }


    @Override
    public OrderInstanceObserver getProtocolInstanceObserver(int orderid, int slotid)
    {
        return isActive() ? new InstanceObserver( getTaskConsumer( orderid, 0 ) ) : OrderInstanceObserver.EMPTY;
    }

}
