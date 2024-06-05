package reptor.test.chronos.message;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.MessageQueue;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.ChronosSystemContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.AbstractTask;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.test.chronos.message.IPongMessages.InternalCount;
import reptor.test.distrbt.com.connect.IntervalPrinter;


public class IPongPushHandlers
{

    public static abstract class IPongPeer extends AbstractTask implements PushMessageSink<InternalCount>
    {
        private final SchedulerContext<? extends ChronosDomainContext>  m_master;
        private final ChronosDomainContext                      m_domcntxt;
        private final ChronosSystemContext                      m_syscntxt;

        private final Random    m_rand;
        private final int       m_minbatch;
        private final int       m_batchvar;

        public IPongPeer(SchedulerContext<? extends ChronosDomainContext> master, ChronosSystemContext syscntxt, IPongConfiguration config)
        {
            m_master   = master;
            m_domcntxt = master.getDomainContext();
            m_syscntxt = syscntxt;
            m_minbatch = config.getMinimumBatchSize();
            m_batchvar = config.getMaximumBatchSize() - m_minbatch + 1;
            m_rand     = m_batchvar==1 ? null : new Random();
        }

        @Override
        protected SchedulerContext<?> master()
        {
            return m_master;
        }

        protected ChronosDomainContext domainContext()
        {
            return m_domcntxt;
        }

        public ChronosDomainContext getDomainContext()
        {
            return m_domcntxt;
        }

        protected long time()
        {
            return m_domcntxt.time();
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
            m_syscntxt.shutdownDomains();
        }
    }


    public static class Receiver extends IPongPeer
                                 implements SchedulerContext<ChronosDomainContext>,
                                            MessageQueueHandler<MessageQueue<InternalCount>>,
                                            DomainEndpoint<PushMessageSink<InternalCount>>
    {
        private final IPongConfiguration                m_config;
        private final IntervalPrinter                   m_monitor;
//        private final ArrayDeque<InternalCount>         m_inqueue;
        private final Portal<InternalCount>             m_inqueue;
        private List<PushMessageSink<InternalCount>>    m_senders;

        public Receiver(SchedulerContext<? extends ChronosDomainContext> master, ChronosSystemContext syscntxt, IPongConfiguration config,
                        Function<MessageQueueHandler<MessageQueue<InternalCount>>, Portal<InternalCount>> portalfac)
        {
            super( master, syscntxt, config );

            m_config   = config;
            m_monitor  = new IntervalPrinter( this, config.getPrintInterval() );
//            m_inqueue  = new ArrayDeque<>();
            m_inqueue  = portalfac.apply( this );
        }

        public void init(List<PushMessageSink<InternalCount>> senders)
        {
            m_senders = senders;
        }

        @Override
        public PushMessageSink<InternalCount> createChannel(ChronosAddress origin)
        {
            return m_inqueue.createChannel( origin );
        }

        public void start()
        {
            domainContext().registerTimer( key -> shutdownSystem() ).schedule( m_config.getDuration() );
            m_monitor.start();
        }

        @Override
        public void taskReady(ChronosTask task)
        {
            notifyReady();
        }

        @Override
        public void messagesReady(MessageQueue<InternalCount> queue)
        {
            notifyReady();
        }

        @Override
        public void enqueueMessage(InternalCount msg)
        {
            m_inqueue.add( msg );

//            notifyReady();
        }

        @Override
        public boolean execute()
        {
            m_inqueue.retrieveMessages();

            InternalCount count;

            long curts = time();

            while( ( count = m_inqueue.poll() )!=null )
            {
                m_monitor.accept( ( curts-count.getStartTime() )/1000  );

                // This is also an unbuffered data source
                m_senders.get( count.getSender() ).enqueueMessage( count );
            }

            return isDone( m_monitor.execute() );
        }
    }


    public static class Sender extends IPongPeer implements MessageQueueHandler<MessageQueue<InternalCount>>,
                                                            DomainEndpoint<PushMessageSink<InternalCount>>
    {
        private final int                        m_senderid;
        private final Portal<InternalCount> m_portal;
        private final InternalCount[]            m_msgs;
        private PushMessageSink<InternalCount>   m_receiver;

        private long            m_counter = 0;
        private long            m_nextval = 0;

        public Sender(SchedulerContext<? extends ChronosDomainContext> master, ChronosSystemContext syscntxt, IPongConfiguration config,
                      int senderid, Function<MessageQueueHandler<MessageQueue<InternalCount>>, Portal<InternalCount>> portalfac)
        {
            super( master, syscntxt, config );

            m_senderid = senderid;
            m_portal   = portalfac.apply( this );

            m_msgs = new InternalCount[ config.getMessageWindowSize() ];
            Arrays.setAll( m_msgs, i -> new InternalCount( senderid ) );
        }

        public void init(PushMessageSink<InternalCount> receiver)
        {
            m_receiver = receiver;
        }

        @Override
        public PushMessageSink<InternalCount> createChannel(ChronosAddress origin)
        {
            return origin==domainContext().getDomainAddress() ? this : m_portal.createChannel( origin );
        }

        public void start()
        {
            notifyReady();
        }

        @Override
        public void messagesReady(MessageQueue<InternalCount> queue)
        {
            notifyReady();
        }

        @Override
        public void enqueueMessage(InternalCount msg)
        {
            messageReceived( msg );

            notifyReady();
        }

        @Override
        public boolean execute()
        {
            boolean isdone;

            long curts = time();
            int  batch = nextBatch();

            m_portal.retrieveMessages();

            InternalCount msg;

            while( ( msg = m_portal.poll() )!=null )
                messageReceived( msg );

            while( true )
            {
                if( m_nextval-m_counter==m_msgs.length )
                {
                    isdone = true;
                    break;
                }
                else if( batch--==0 )
                {
                    isdone = false;
                    break;
                }

                InternalCount count = m_msgs[ (int) ( m_nextval % m_msgs.length ) ];
                count.init( m_nextval++, curts );

                m_receiver.enqueueMessage( count );
            }

            return isDone( isdone );
        }


        public void messageReceived(InternalCount msg)
        {
            assert msg.getSender()==m_senderid;
            assert msg.getNumber()==m_counter;

            m_counter++;
        }
    }


    public static class PublicReceiver extends AbstractMaster<ChronosDomainContext>
                                       implements MessageQueueHandler<MessageQueue<InternalCount>>,
                                                  DomainEndpoint<PushMessageSink<InternalCount>>
    {
        private final SchedulerContext<? extends ChronosDomainContext>  m_master;
        private final Receiver                      m_peer;
        private final Portal<InternalCount>         m_portal;

        public PublicReceiver(SchedulerContext<? extends ChronosDomainContext> master, ChronosSystemContext syscntxt,
                              IPongConfiguration config, Function<MessageQueueHandler<MessageQueue<InternalCount>>, Portal<InternalCount>> portalfac)
        {
            m_master = master;
            m_peer   = new Receiver( this, syscntxt, config, UnsafePortal<InternalCount>::new );
            m_portal = portalfac.apply( this );
        }

        @Override
        protected SchedulerContext<? extends ChronosDomainContext> master()
        {
            return m_master;
        }

        public Receiver getPeer()
        {
            return m_peer;
        }

        public void init(List<PushMessageSink<InternalCount>> senders)
        {
            m_peer.init( senders );
        }

        public void start()
        {
            m_peer.start();
        }

        @Override
        public PushMessageSink<InternalCount> createChannel(ChronosAddress origin)
        {
            return m_portal.createChannel( origin );
        }

        @Override
        public void taskReady(ChronosTask task)
        {
            notifyReady();
        }

        @Override
        public void messagesReady(MessageQueue<InternalCount> queue)
        {
            notifyReady();
        }

        @Override
        public boolean execute()
        {
            m_portal.retrieveMessages();

            InternalCount msg;

            while( ( msg = m_portal.poll() )!=null )
                m_peer.enqueueMessage( msg );

            boolean isdone = m_peer.execute();

            return isDone( isdone && !m_portal.isReady() );
        }
    }



    public static class PublicSender extends AbstractMaster<ChronosDomainContext>
                                       implements MessageQueueHandler<MessageQueue<InternalCount>>,
                                                  DomainEndpoint<PushMessageSink<InternalCount>>
    {
        private final SchedulerContext<? extends ChronosDomainContext>  m_master;
        private final Sender                        m_peer;
        private final Portal<InternalCount>         m_portal;

        public PublicSender(SchedulerContext<? extends ChronosDomainContext> master, ChronosSystemContext syscntxt, IPongConfiguration config,
                            int senderid, Function<MessageQueueHandler<MessageQueue<InternalCount>>, Portal<InternalCount>> portalfac)
        {
            m_master = master;
            m_peer   = new Sender( this, syscntxt, config, senderid, UnsafePortal<InternalCount>::new );
            m_portal = portalfac.apply( this );
        }

        @Override
        protected SchedulerContext<? extends ChronosDomainContext> master()
        {
            return m_master;
        }

        public Sender getPeer()
        {
            return m_peer;
        }

        public void init(PushMessageSink<InternalCount> receiver)
        {
            m_peer.init( receiver );
        }

        public void start()
        {
            m_peer.start();
        }

        @Override
        public PushMessageSink<InternalCount> createChannel(ChronosAddress origin)
        {
            return m_portal.createChannel( origin );
        }

        @Override
        public void taskReady(ChronosTask task)
        {
            notifyReady();
        }

        @Override
        public void messagesReady(MessageQueue<InternalCount> queue)
        {
            notifyReady();
        }

        @Override
        public boolean execute()
        {
            m_portal.retrieveMessages();

            InternalCount msg;

            while( ( msg = m_portal.poll() )!=null )
                m_peer.enqueueMessage( msg );

            boolean isdone = m_peer.execute();

            return isDone( isdone && !m_portal.isReady() );
        }
    }

}
