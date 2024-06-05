package reptor.test.chronos.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiFunction;

import ch.qos.logback.classic.Level;
import reptor.chronos.ChronosAddress;
import reptor.chronos.com.MessageQueue;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.domains.DomainController;
import reptor.chronos.domains.GenericDomain;
import reptor.chronos.domains.SimpleDomain;
import reptor.chronos.orphics.AddressName;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.chronos.portals.MultiQueuePortal;
import reptor.chronos.schedule.GenericSchedulerTask;
import reptor.chronos.schedule.PairScheduler;
import reptor.chronos.schedule.SingleRoundScheduler;
import reptor.jlib.collect.Slots;
import reptor.test.chronos.message.IPongBlockingHandlers.BlockingReceiver;
import reptor.test.chronos.message.IPongBlockingHandlers.BlockingSender;
import reptor.test.chronos.message.IPongMessages.InternalCount;
import reptor.test.chronos.message.IPongPushHandlers.PublicReceiver;
import reptor.test.chronos.message.IPongPushHandlers.PublicSender;
import reptor.test.chronos.message.IPongPushHandlers.Receiver;
import reptor.test.chronos.message.IPongPushHandlers.Sender;
import reptor.test.common.Logging;


public class MessageTest
{

    static
    {
        Logging.initRootLogger( Level.INFO );
//        Logging.initRootLogger( Level.ALL );
//        SslConfiguration.enableDebuggingOutput();
    }


    static Slots<GenericDomain<? extends ChronosDomainContext>> createDomains(int ndoms)
    {
        ChronosAddress[] domaddrs = new ChronosAddress[ ndoms ];
        Arrays.setAll( domaddrs, domno -> new AddressName( "MSGTEST" + domno ) );

        Slots<GenericDomain<? extends ChronosDomainContext>> domains = new Slots<>( ndoms );

        for( int domno=0; domno<ndoms; domno++ )
            domains.put( domno, new SimpleDomain( domaddrs[ domno ], domaddrs ) );

        return domains;
    }


    static void executeSingleThreaded(IPongConfiguration pongconf,
            BiFunction<MessageQueueHandler<MessageQueue<InternalCount>>, ChronosAddress[], Portal<InternalCount>> portalfac) throws Exception
    {
        Slots<GenericDomain<? extends ChronosDomainContext>> domains = createDomains( 1 );

        DomainController domctrl = DomainController.createForDomains( domains );

        GenericSchedulerTask<ChronosDomainContext> sched = new PairScheduler<>();
        domains.get( 0 ).bindTask( sched );

        Receiver receiver = new Receiver( sched.getContext(), domctrl, pongconf, h -> portalfac.apply( h, new ChronosAddress[ 0 ] ) );
        sched.registerTask( receiver );
        Sender sender = new Sender( sched.getContext(), domctrl, pongconf, 0, h -> portalfac.apply( h, new ChronosAddress[ 0 ] ) );
        sched.registerTask( sender );

        sender.init( receiver );
        receiver.init( Collections.singletonList( sender ) );

        receiver.start();
        sender.start();

        domctrl.getDomains().get( 0 ).execute();
    }


    static void executePublicSingleThreaded(int nsenders, IPongConfiguration pongconf,
            BiFunction<MessageQueueHandler<MessageQueue<InternalCount>>, ChronosAddress[], Portal<InternalCount>> portalfac) throws Exception
    {
        Slots<GenericDomain<? extends ChronosDomainContext>> domains = createDomains( 1 );

        DomainController domctrl = DomainController.createForDomains( domains );

        GenericSchedulerTask<ChronosDomainContext> sched = new SingleRoundScheduler<>();
        domains.get( 0 ).bindTask( sched );

        List<PushMessageSink<InternalCount>> senderchannels = new ArrayList<>();

        ChronosAddress[] recvaddr    = new ChronosAddress[] { domains.get( 0 ).getAddress() };
        ChronosAddress[] senderaddrs = new ChronosAddress[] { domains.get( 0 ).getAddress() };

        PublicReceiver receiver = new PublicReceiver( sched.getContext(), domctrl, pongconf, h -> portalfac.apply( h, senderaddrs ) );
        sched.registerTask( receiver );

        PublicSender[] senders = new PublicSender[ nsenders ];
        for( int senderid=0; senderid<nsenders; senderid++ )
        {
            PublicSender sender = new PublicSender( sched.getContext(), domctrl, pongconf, senderid, h -> portalfac.apply( h, recvaddr ) );
            sched.registerTask( sender );

            sender.init( receiver.createChannel( domains.get( 0 ).getAddress() ) );
            senderchannels.add( sender.createChannel( domains.get( 0 ).getAddress() ) );

            senders[ senderid ] = sender;
        }

        receiver.init( senderchannels );

        receiver.start();
        for( PublicSender sender : senders )
            sender.start();

        domctrl.getDomains().get( 0 ).execute();
    }


    static void executeMultiThreaded(int nsenderdoms, IPongConfiguration pongconf,
            BiFunction<MessageQueueHandler<MessageQueue<InternalCount>>, ChronosAddress[], Portal<InternalCount>> portalfac) throws Exception
    {
        // Create domains
        Slots<GenericDomain<? extends ChronosDomainContext>> domains = createDomains( nsenderdoms+1 );

        DomainController domctrl = DomainController.createForDomains( domains );

        // Create handlers
        List<PushMessageSink<InternalCount>> senderchannels = new ArrayList<>();

        ChronosAddress[] recvaddr    = new ChronosAddress[] { domains.get( 0 ).getAddress() };
        ChronosAddress[] senderaddrs = new ChronosAddress[ nsenderdoms ];
        Arrays.setAll( senderaddrs, i -> domains.get( i+1 ).getAddress() );

        Receiver receiver = new Receiver( domains.get( 0 ).getContext(), domctrl, pongconf, h -> portalfac.apply( h, senderaddrs ) );
        domains.get( 0 ).registerTask( receiver );

        Sender[] senders = new Sender[ nsenderdoms ];
        for( int senderid=0; senderid<nsenderdoms; senderid++ )
        {
            GenericDomain<? extends ChronosDomainContext> dom = domains.get( senderid+1 );

            Sender sender = new Sender( dom.getContext(), domctrl, pongconf, senderid, h -> portalfac.apply( h, recvaddr ) );
            dom.registerTask( sender );

            sender.init( receiver.createChannel( dom.getAddress() ) );
            senderchannels.add( sender.createChannel( domains.get( 0 ).getAddress() ) );

            senders[ senderid ] = sender;
        }

        receiver.init( senderchannels );

        receiver.start();
        for( Sender sender : senders )
            sender.start();

        // Execute
        domctrl.executeDomains();
    }


    static void executeBlocking(int nsenders, IPongConfiguration pongconf) throws InterruptedException
    {
        BlockingReceiver receiver = new BlockingReceiver( pongconf, "RECEIVER" );

        List<BlockingQueue<InternalCount>> senderqueues = new ArrayList<>( nsenders );
        BlockingSender[] senders = new BlockingSender[ nsenders ];
        for( int senderid=0; senderid<nsenders; senderid++ )
        {
            senders[ senderid ] = new BlockingSender( pongconf, senderid, "SENDER" + senderid );
            senders[ senderid ].init( receiver.getInQueue() );
            senderqueues.add( senders[ senderid ].getInQueue() );
        }

        receiver.init( senderqueues );

        receiver.start();
        for( BlockingSender sender : senders )
            sender.start();

        receiver.join();
    }


    public static void main(String[] args) throws Exception
    {
        IPongConfigurator pongconf = new IPongConfigurator();

        BiFunction<MessageQueueHandler<MessageQueue<InternalCount>>, ChronosAddress[], Portal<InternalCount>> portalfac;
//        portalfac = (h, a) -> new QueuePortal<>( h );
        portalfac = MultiQueuePortal<InternalCount>::new;
//        portalfac = (h, a) -> new NonbockingQueuePortal<>( h );
//        portalfac = (h, a) -> new ConcurrentPortal<>( h );

//        executeSingleThreaded( pongconf, portalfac );
        executeMultiThreaded( 8, pongconf, portalfac );
//        executePublicSingleThreaded( 1, pongconf, portalfac );
//        executeBlocking( 8, pongconf );

        System.out.println( "exit." );
    }

}
