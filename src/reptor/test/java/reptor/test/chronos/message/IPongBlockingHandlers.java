package reptor.test.chronos.message;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import reptor.bench.IntervalResultFormatter;
import reptor.measr.sink.LongStatsSink;
import reptor.test.chronos.message.IPongMessages.InternalCount;


public class IPongBlockingHandlers
{

    public static abstract class IPongBlockingPeer extends Thread
    {
        protected final BlockingQueue<InternalCount> m_inqueue;

        private final Random    m_rand;
        private final int       m_minbatch;
        private final int       m_batchvar;

        public IPongBlockingPeer(IPongConfiguration config, String name)
        {
            super( name );

            m_inqueue  = new LinkedBlockingQueue<>();
            m_minbatch = config.getMinimumBatchSize();
            m_batchvar = config.getMaximumBatchSize() - m_minbatch + 1;
            m_rand     = m_batchvar==1 ? null : new Random();
        }

        public BlockingQueue<InternalCount> getInQueue()
        {
            return m_inqueue;
        }

        protected long time()
        {
            return System.nanoTime();
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

        protected void shutdownSystem()
        {
            System.exit( 0 );
        }
    }


    public static class BlockingReceiver extends IPongBlockingPeer
    {
        private List<BlockingQueue<InternalCount>>  m_senders;
        private Timer                               m_timer = new Timer( true );

        private final LongStatsSink         m_intsum = new LongStatsSink();
        private long                        m_intts;
        private int                         m_intno  = 0;


        public BlockingReceiver(IPongConfiguration config, String name)
        {
            super( config, name );

            m_timer.scheduleAtFixedRate( new TimerTask()
                                         {
                                             @Override
                                             public void run() { interrupt(); }
                                         },
                                         1000, 1000 );
            m_intts = time();
        }

        public void init(List<BlockingQueue<InternalCount>> senders)
        {
            m_senders = senders;
        }

        @Override
        public void run()
        {
            InternalCount count;

            while( true )
            {
                try
                {
                    while( ( count = m_inqueue.take() )!=null )
                    {
                        long curts = time();

                        m_intsum.accept( ( curts-count.getStartTime() )/1000  );

                        // This is also an unbuffered data source
                        m_senders.get( count.getSender() ).add( count );
                    }

                    return;
                }
                catch( InterruptedException e )
                {
                    long curts = time();

                    IntervalResultFormatter.printInterval( null, ++m_intno, curts-m_intts, m_intsum );

                    m_intsum.reset();
                    m_intts = curts;
                }
            }
        }
    }


    public static class BlockingSender extends IPongBlockingPeer
    {
        private final int                           m_senderid;
        private final InternalCount[]               m_msgs;
        private BlockingQueue<InternalCount>        m_receiver;

        private long            m_counter = 0;
        private long            m_nextval = 0;

        public BlockingSender(IPongConfiguration config, int senderid, String name)
        {
            super( config, name );

            m_senderid = senderid;

            m_msgs = new InternalCount[ config.getMessageWindowSize() ];
            Arrays.setAll( m_msgs, i -> new InternalCount( senderid ) );
        }

        public void init(BlockingQueue<InternalCount> receiver)
        {
            m_receiver = receiver;
        }

        @Override
        public void run()
        {
            try
            {
                while( true )
                {
                    long curts = time();

                    while( m_nextval-m_counter<m_msgs.length )
                    {
                        InternalCount count = m_msgs[ (int) ( m_nextval % m_msgs.length ) ];
                        count.init( m_nextval++, curts );

                        m_receiver.put( count );
                    }

                    messageReceived( m_inqueue.take() );
                }
            }
            catch( InterruptedException e )
            {
                throw new IllegalStateException( e );
            }
        }

        public void messageReceived(InternalCount msg)
        {
            assert msg.getSender()==m_senderid;
            assert msg.getNumber()==m_counter;

            m_counter++;
        }
    }

}
