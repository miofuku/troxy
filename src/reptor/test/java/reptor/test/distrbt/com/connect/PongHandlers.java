package reptor.test.distrbt.com.connect;

import java.util.Random;

import reptor.chronos.ChronosTask;
import reptor.chronos.Immutable;
import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.test.distrbt.com.connect.PongMessages.Count;


public class PongHandlers
{

    @Immutable
    public static class PongConfiguration
    {
        protected int   m_payload  = 0;
        protected int   m_wndsize  = 10;
        protected int   m_minbatch = 10;
        protected int   m_maxbatch = 10;
        protected int   m_duration = 30000;
        protected int   m_printint = 1000;

        public int getPayloadSize()         { return m_payload; }
        public int getMessageWindowSize()   { return m_wndsize; }
        public int getMinimumBatchSize()    { return m_minbatch; }
        public int getMaximumBatchSize()    { return m_maxbatch; }
        public int getDuration()            { return m_duration; }
        public int getPrintInterval()       { return m_printint; }
    }


    public static class PongConfigurator extends PongConfiguration
    {
        public void setPayloadSize(int payload)         { m_payload = payload; }
        public void setMessageWindowSize(int wndsize)   { m_wndsize = wndsize; }
        public void setMinimumBatchSize(int maxbatch)   { m_minbatch = maxbatch; }
        public void setMaximumBatchSize(int minbatch)   { m_maxbatch = minbatch; }
        public void setDuration(int duration)           { m_duration = duration; }
        public void setPrintInterval(int printint)      { m_printint = printint; }
    }


    public static abstract class PongPeer extends SingleConnectionPeer
    {
        private final Random    m_rand;
        private final int       m_minbatch;
        private final int       m_batchvar;

        public PongPeer(Context cntxt, PongConfiguration config)
        {
            super( cntxt );

            m_minbatch = config.getMinimumBatchSize();
            m_batchvar = config.getMaximumBatchSize() - m_minbatch + 1;
            m_rand     = m_batchvar==1 ? null : new Random();
        }

        protected int nextBatch()
        {
            if( m_rand==null )
                return m_minbatch;
            else
            {
                return m_minbatch + m_rand.nextInt( m_batchvar );
            }
        }
    }


    public static class PongServer extends PongPeer
    {
        private long    m_counter = 0;

        public PongServer(Context cntxt, PongConfiguration config)
        {
            super( cntxt, config );
        }

        @Override
        public boolean execute()
        {
            Count count;

            int batch = nextBatch();

            while( batch-->0 && ( count = (Count) inQueue().poll() )!=null )
            {
                assert count.getNumber()==m_counter;

                m_counter++;

                peerChannel().enqueueMessage( count );
            }

            return isDone( inQueue().isEmpty() );
        }
    }


    public static class PongClient extends PongPeer implements SchedulerContext<SelectorDomainContext>
    {
        private final SelectorDomainContext m_domcntxt;

        private final IntervalPrinter   m_monitor;
        private final PongConfiguration m_config;
        private final int               m_payload;

        private long            m_counter = 0;
        private long            m_nextval = 0;
        private long[]          m_times;

        public PongClient(Context cntxt, PongConfiguration config)
        {
            super( cntxt, config );

            m_domcntxt = cntxt.getDomainContext();
            m_monitor  = new IntervalPrinter( this, config.getPrintInterval() );
            m_config   = config;
            m_payload  = config.getPayloadSize();
            m_times    = new long[ config.getMessageWindowSize() ];
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
        public void connectionEstablished()
        {
            super.connectionEstablished();

            m_domcntxt.registerTimer( key -> shutdownSystem() ).schedule( m_config.getDuration() );
            m_monitor.start();
        }

        @Override
        public void taskReady(ChronosTask task)
        {
            notifyReady();
        }

        @Override
        public boolean execute()
        {
            Count count;

            long curts = time();

            // Process messages received back
            while( ( count = (Count) inQueue().poll() )!=null )
            {
                long startts = m_times[ (int) ( m_counter % m_times.length ) ];

                m_monitor.accept( ( curts-startts )/1000  );

                assert count.getNumber()==m_counter;

                m_counter++;
            }

            // Generate new messages
            boolean isdone;

            int batch = nextBatch();

            while( true )
            {
                if( m_nextval-m_counter==m_times.length )
                {
                    isdone = true;
                    break;
                }
                else if( batch--==0 )
                {
                    isdone = false;
                    break;
                }

                m_times[ (int) ( m_nextval % m_times.length ) ] = curts;
                count = new Count( m_nextval++, m_payload );
                count.setValid();

                mapper().serializeMessage( count );
                peerChannel().enqueueMessage( count );
            }

            // Execute subjects
            m_monitor.execute();

            return isDone( isdone );
        }

    }

}
