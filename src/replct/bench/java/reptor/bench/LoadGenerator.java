package reptor.bench;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import reptor.bench.LoadGenerator.StartBenchmark;
import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.MessageQueue;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.Actor;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.chronos.portals.QueuePortal;
import reptor.distrbt.com.Message;
import reptor.distrbt.domains.SelectorDomainContext;


public class LoadGenerator extends AbstractMaster<SelectorDomainContext>
					       implements Actor, DomainEndpoint<PushMessageSink<StartBenchmark>>,
					                  MessageQueueHandler<MessageQueue<StartBenchmark>>
{

    public static class StartBenchmark implements Message
    {
        @Override
        public int getTypeID()
        {
            throw new UnsupportedOperationException();
        }
    }

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final SchedulerContext<? extends SelectorDomainContext> m_cntxt;

    private final short                         m_genno;
    private final BenchmarkClient[]             m_clients;
    private final int                           m_maxinvs;
    private final QueuePortal<StartBenchmark>   m_portal;

    private final Queue<BenchmarkClient>        m_readyclients;

    private boolean                             m_isstarted = false;


    public LoadGenerator(SchedulerContext<? extends SelectorDomainContext> cntxt, short genno, int nclients, int maxinvs)
                                   throws IOException
    {
        m_cntxt        = cntxt;
        m_genno        = genno;
        m_maxinvs      = maxinvs;
        m_clients      = new BenchmarkClient[ nclients ];
        m_readyclients = new ArrayDeque<>( nclients );
        m_portal       = new QueuePortal<>( this );
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    //-------------------------------------//
    //               General               //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "LOAD%02d", m_genno );
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initClient(int index, BenchmarkClient client)
    {
        assert m_clients[ index ]==null;

        m_clients[ index ] = client;
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public PushMessageSink<StartBenchmark> createChannel(ChronosAddress origin)
    {
        return m_portal.createChannel( origin );
    }


    @Override
    public void messagesReady(MessageQueue<StartBenchmark> queue)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( !m_isstarted )
        {
            m_portal.retrieveMessages();

            if( m_portal.poll()!=null )
            {
                startBenchmark();

                m_isstarted = true;
            }
        }

        BenchmarkClient client;

        while( ( client = m_readyclients.poll() )!=null )
        {
            client.execute();

            if( m_isstarted )
                issueRequests( client );
        }

        clearReady();

        return true;
    }


    private void startBenchmark()
    {
        for( BenchmarkClient client : m_clients )
            if( client!=null )
                issueRequests( client );
    }


    private void issueRequests(BenchmarkClient client)
    {
        int nnewinvs = m_maxinvs - client.getNumberOfOngoingInvocations();

        while( nnewinvs-->0 )
            client.startInvocation();
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public void taskReady(ChronosTask task)
    {
        m_readyclients.add( (BenchmarkClient) task );

        notifyReady();
    }

}
