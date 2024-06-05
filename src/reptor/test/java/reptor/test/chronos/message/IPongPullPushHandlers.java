package reptor.test.chronos.message;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

import reptor.chronos.ChronosTask;
import reptor.chronos.Immutable;
import reptor.chronos.com.BufferedMessageSource;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.com.UnbufferedMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractTask;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.test.chronos.message.IPongMessages.InternalCount;
import reptor.test.distrbt.com.connect.IntervalPrinter;

public class IPongPullPushHandlers
{

    @Immutable
    public static class InternalPongConfiguration
    {
        protected int   m_wndsize  = 10;
        protected int   m_minbatch = 10;
        protected int   m_maxbatch = 10;
        protected int   m_duration = 30000;
        protected int   m_printint = 1000;

        public int getMessageWindowSize()   { return m_wndsize; }
        public int getMinimumBatchSize()    { return m_minbatch; }
        public int getMaximumBatchSize()    { return m_maxbatch; }
        public int getDuration()            { return m_duration; }
        public int getPrintInterval()       { return m_printint; }
    }


    public static class Receiver extends AbstractTask
                                 implements SchedulerContext<SelectorDomainContext>, UnbufferedMessageSink<InternalCount>
    {

        private final SchedulerContext<SelectorDomainContext>   m_master;
        private final SelectorDomainContext                     m_domcntxt;

        private final IntervalPrinter                           m_monitor;
        private final List<PushMessageSink<InternalCount>>      m_senders;

        public Receiver(SchedulerContext<SelectorDomainContext> master)
        {
            m_master   = master;
            m_domcntxt = master.getDomainContext();
            m_monitor  = new IntervalPrinter( this, 1000 );
            m_senders  = new ArrayList<>();
        }

        @Override
        protected SchedulerContext<?> master()
        {
            return m_master;
        }

        private long time()
        {
            return m_domcntxt.time();
        }

        @Override
        public SelectorDomainContext getDomainContext()
        {
            return m_domcntxt;
        }

        @Override
        public void taskReady(ChronosTask task)
        {
            notifyReady();
        }

        @Override
        public boolean canProcessMessages()
        {
            return true;
        }

        @Override
        public void processMessages(Queue<InternalCount> msgs)
        {
            InternalCount count;

            long curts = time();

            while( ( count = msgs.poll() )!=null )
            {
                m_monitor.accept( ( curts-count.getStartTime() )/1000  );

                // This is also an unbuffered data source
                m_senders.get( count.getSender() ).enqueueMessage( count );
            }
        }

        @Override
        public boolean execute()
        {
            return m_monitor.execute();
        }
    }


    public static class Sender implements BufferedMessageSource<InternalCount>, PushMessageSink<InternalCount>
    {
        public Sender(int senderid)
        {
        }

        // TODO: This is a buffered message sink or a push message sink
        @Override
        public void enqueueMessage(InternalCount msg)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean isReady()
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean execute()
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean hasUnprocessedMessages()
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean hasMessages()
        {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Deque<InternalCount> startMessageProcessing()
        {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void finishMessageProcessing()
        {
        }
    }
}
