package reptor.replct.invoke.bft;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.connect.NetworkHandshakeWorker;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.StandardHandshakeState;
import reptor.replct.invoke.InvocationClientHandler;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.service.ServiceCommand;


public abstract class BFTClientHandler extends AbstractMaster<SelectorDomainContext>
                                       implements InvocationClientHandler, PushMessageSink<Message>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    protected static final Logger s_logger = LoggerFactory.getLogger( BFTClientHandler.class );

    private final SchedulerContext<? extends SelectorDomainContext> m_master;

    private final short                     m_clino;
    private final HandshakingProcess<?>     m_handshake;
    private final NetworkHandshakeWorker    m_connector;

    private final Queue<Reply>  m_replies;
    private long                m_nextinvno;
    private long                m_newinv;
    private long                m_barrier;
    private long                m_waitfor;
    private long                m_nextres;
    protected byte              m_contact;
    private boolean             isprophecy;

    private Map<Long, ServiceCommand> pendingCommands = new TreeMap<>();

    private long                sequence=1, isconflict=0;

    public BFTClientHandler(SchedulerContext<? extends SelectorDomainContext> master, short clino, byte contact,
                            BFTInvocationClient invcli)
    {
        m_master    = master;
        m_clino     = clino;

        m_handshake = invcli.createHandshake( clino );
        m_connector = new NetworkHandshakeWorker( this, toString(), m_clino, 1, m_handshake::createHandlers, 100 );

        m_replies    = new ArrayDeque<>();
        m_nextinvno  = BFTInvocation.FIRST_INVOCATION;
        m_newinv     = BFTInvocation.NO_INVOCATION;
        m_barrier    = BFTInvocation.NO_INVOCATION;
        m_waitfor    = BFTInvocation.NO_INVOCATION;
        m_nextres    = BFTInvocation.FIRST_INVOCATION;
        m_contact    = contact;
        isprophecy   = invcli.getInvocation().isProphecy();
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    protected abstract FixedSlidingWindow<? extends BFTClientInstance> invocations();


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "INVCH[%03d]", m_clino );
    }


    @Override
    public int getNumberOfOngoingInvocations()
    {
        return (int) ( m_nextinvno-m_nextres );
    }


    @Override
    public int getMaximumNumberOfInvocations()
    {
        return invocations().size();
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void startInvocation(ServiceCommand command)
    {
        long invno = m_nextinvno++;

        prepareInvocation( invno ).init( invno, m_contact, command, false );

        if( m_newinv==BFTInvocation.NO_INVOCATION )
        {
            m_newinv = invno;

            // if m_barrier!=NO_INVOCATION then m_barrier<m_newinv, hence the new invocation cannot be ready yet.
            if( m_barrier==BFTInvocation.NO_INVOCATION )
                notifyReady();
        }
        else if( invocations().isBelowWindow( m_newinv ) )
        {
            m_newinv = invocations().getWindowStart();
        }
    }


    // TODO: Use a synchronous interface for connections and remove the reply queue.
    @Override
    public void enqueueMessage(Message msg)
    {
        enqueueReply( (Reply) msg );
    }


    public void enqueueReply(Reply reply)
    {
        m_replies.add( reply );
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( !isConnected() )
        {
            // Processing new connections is presumably activating.
            markReady();

            while( m_connector.isReady() )
            {
                m_connector.execute();

                StandardHandshakeState hsstate;

                // Retrieving results can change the ready state!
                while( ( hsstate = (StandardHandshakeState) m_connector.pollNewConnections() )!=null )
                    processNewConnection( hsstate );
            }

            return isDone( !isConnected() );
        }
        else
        {
            if( newInvocationsReady() )
                processNewInvocations();

            if( isConnectionReady() )
                processConnection();

            Reply reply;

            while( ( reply = m_replies.poll() )!=null )
                handleReply( reply );

            // handleReply could yield new outgoing messages and the barrier could be ready or could have been removed.
            return isDone( !isConnectionReady() && !newInvocationsReady() );
        }
    }


    protected void startConnection(short remno, InetSocketAddress addr)
    {
        m_connector.startConnection( m_handshake.createConnectionArguments( new RemoteEndpoint( remno, (short) 0 ), addr ) );
    }


    protected abstract void processNewConnection(StandardHandshakeState hsstate);


    private boolean newInvocationsReady()
    {
        return m_newinv!=BFTInvocation.NO_INVOCATION && ( m_barrier==BFTInvocation.NO_INVOCATION ||
                m_newinv<m_barrier || m_newinv==m_barrier && m_waitfor==BFTInvocation.NO_INVOCATION );
    }


    private void processNewInvocations()
    {
        assert invocations().isWithinWindow( m_newinv );

        do
        {
            BFTClientInstance inv = invocations().getSlotUnchecked( m_newinv );

            if( inv.getCommand()==null )
                break;

            if( !inv.isBarrier() )
                inv.startInvocation();
            else
            {
                assert m_barrier==BFTInvocation.NO_INVOCATION || m_barrier==m_newinv;

                if( m_barrier==BFTInvocation.NO_INVOCATION )
                {
                    m_barrier = m_newinv;
                    m_waitfor = m_newinv-1;

                    checkWaitFor();
                }

                // A barrier invocation may only be started if all previous invocations have been finished.
                if( m_waitfor==BFTInvocation.NO_INVOCATION )
                {
                    inv.startInvocation();

                    if( invocations().isAboveWindow( ++m_newinv ) )
                        m_newinv = BFTInvocation.NO_INVOCATION;
                }

                return;
            }
        }
        while( !invocations().isAboveWindow( ++m_newinv ) );

        m_newinv = BFTInvocation.NO_INVOCATION;
    }


    protected abstract boolean isConnectionReady();


    protected abstract void processConnection();


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    public boolean handleReply(Reply reply)
    {
        long invno = reply.getInvocationNumber();

        if( invocations().isBelowWindow( invno ) )
        {
            s_logger.debug( "{} reply {} is outdated; window starts at {}", this, reply, invocations().getWindowStart() );

            return false;
        }
        else if( invocations().isAboveWindow( invno ) )
        {
            s_logger.debug( "{} reply {} is beyond; window ends at {}", this, reply, invocations().getWindowEnd() );

            throw new UnsupportedOperationException();
        }
        else
        {
            BFTClientInstance inv = invocations().getSlotUnchecked( invno );

            if( !inv.handleReply( reply ) )
                return false;
            else if( !inv.isStable() )
            {
//                assert invno==m_barrier && !inv.isBarrier();

//                m_barrier = BFTInvocation.NO_INVOCATION;
                sequence = invno;
                isconflict++;

                ServiceCommand re_command = inv.getCommand();

                if (invno>invocations().getWindowStart())
                {
                    pendingCommands.put(invno,re_command);
                }
                else
                {
                    startReinvocation(re_command);
                    getPendingCommand(invno+1);
                }

                return false;
            }
            else
            {
                sequence = invno;

                if( invno==m_barrier )
                    m_barrier = BFTInvocation.NO_INVOCATION;
                else if( invno==m_waitfor )
                {
                    m_waitfor--;

                    checkWaitFor();
                }

                invocationCompleted( inv );

                return invno==m_nextres;
            }
        }
    }


    @Override
    public void getConflict()
    {
        System.out.println("Client "+m_clino+" conflict rate: "+isconflict + " " +sequence);
    }


    private void getPendingCommand(long pending)
    {
        while (pendingCommands.containsKey(pending))
        {
            m_nextres=pending+1;
            startReinvocation(pendingCommands.get(pending));
            pendingCommands.remove(pending);
            pending++;
        }
    }


    public void startReinvocation(ServiceCommand command)
    {
        long invno = m_nextinvno++;

        prepareInvocation( invno ).init( invno, m_contact, command, true );

        if( m_newinv==BFTInvocation.NO_INVOCATION )
        {
            m_newinv = invno;

            // if m_barrier!=NO_INVOCATION then m_barrier<m_newinv, hence the new invocation cannot be ready yet.
            if( m_barrier==BFTInvocation.NO_INVOCATION )
                notifyReady();
        }
        else if( invocations().isBelowWindow( m_newinv ) )
        {
            m_newinv = invocations().getWindowStart();
        }
    }


    protected abstract void invocationCompleted(BFTClientInstance inv);


    @Override
    public ServiceCommand pollResult()
    {
        if( invocations().isAboveWindow( m_nextres ) )
            return null;

        BFTClientInstance next = invocations().getSlotUnchecked( m_nextres );

        if( !next.isStable() )
        {
            long pending = next.getInvocationNumber();
            getPendingCommand(pending);

            return null;
        }
        else
        {
            m_nextres++;

            return next.getCommand();
        }
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private BFTClientInstance prepareInvocation(long invno)
    {
        if( invocations().isAboveWindow( invno ) )
        {
            long newwndstart = invno - invocations().size() + 1;

            invocations().forwardWindow( newwndstart );

            if( m_nextres<newwndstart )
                m_nextres = newwndstart;
            if( m_barrier!=BFTInvocation.NO_INVOCATION && m_barrier<newwndstart )
                m_barrier = BFTInvocation.NO_INVOCATION;
            if( m_waitfor!=BFTInvocation.NO_INVOCATION && m_waitfor<newwndstart )
                m_waitfor = BFTInvocation.NO_INVOCATION;
        }

        return invocations().getSlot( invno );
    }


    private void checkWaitFor()
    {
        assert m_waitfor!=BFTInvocation.NO_INVOCATION;

        while( true )
        {
            if( invocations().isBelowWindow( m_waitfor ) )
            {
                m_waitfor = BFTInvocation.NO_INVOCATION;
                break;
            }
            else if( !invocations().getSlotUnchecked( m_waitfor ).isStable() )
            {
                break;
            }
            else
            {
                m_waitfor--;
            }
        }
    }

}
