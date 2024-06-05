package reptor.replct.invoke.bft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.connect.PushNetworkTransmissionConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.replct.connect.CommunicationMessages.NewConnection;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.InvocationReplicaHandler;


public abstract class BFTReplicaHandler extends AbstractMaster<SelectorDomainContext>
                                        implements InvocationReplicaHandler, PushMessageSink<Message>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( BFTReplicaHandler.class );

    private final SchedulerContext<? extends SelectorDomainContext> m_master;

    protected final short                                   m_shardno;
    protected final short                                   m_clino;

    private final PushNetworkTransmissionConnection         m_cliconn;
    private final FixedSlidingWindow<BFTReplicaInstance>    m_invwnd;

    private long                          m_readycmdhigh = 0L;  // inclusive
    private long                          m_readycmdlow  = 0L;  // exclusive


    public BFTReplicaHandler(SchedulerContext<? extends SelectorDomainContext> master, short clintshard, short clino,
                             PushNetworkTransmissionConnection cliconn, BFTReplicaInstance[] slots)
    {
        m_master  = master;
        m_shardno = clintshard;
        m_clino   = clino;

        m_cliconn = cliconn;
        m_cliconn.bindToMaster( this );
        m_cliconn.getInbound().initReceiver( this );

        m_invwnd = new FixedSlidingWindow<>( slots, BFTInvocation.FIRST_INVOCATION );
        m_invwnd.forEach( (invno, slot) -> slot.initInvocation( invno ) );
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    //-------------------------------------//
    //            General State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "INVRH[%02d-%03d]", m_shardno, m_clino );
    }


    @Override
    public short getClientNumber()
    {
        return m_clino;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    @Override
    public void initContact(byte contact)
    {
        m_invwnd.forEach( inv -> inv.initContact( contact ) );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    // TODO: Use a synchronous interface for connections and remove the reply queue.
    @Override
    public void enqueueMessage(Message msg)
    {
        enqueueRequest( (Request) msg );
    }


    @Override
    public void enqueueNewConnection(NewConnection newconn)
    {
        assert newconn.getRemoteEndpoint().getProcessNumber()==m_clino;

        m_cliconn.open( newconn.getState() );
    }


    @Override
    public void enqueueRequest(Request request)
    {
        long invono = request.getNumber();

        if( m_invwnd.isBelowWindow( invono ) )
            s_logger.debug( "{} drop outdated request {}", this, request );
        else
        {
            BFTReplicaInstance inv = m_invwnd.tryGetSlot( invono );

            // TODO: request.isPanic() -> create a full reply. (Can we detect when a client sends a panic as a first request?)
            if( inv!=null && !inv.isRequestRequired( request ) )
            {
                s_logger.debug( "{} received request {} not required", this, request );
            }
            else
            {
                // The window is adjusted according to the received request. This is only allowed if the request
                // is valid. That's why the verification cannot be part of the instance.
                verifyRequest( request );

                if( inv==null )
                    inv = prepareInvocation( invono );

                if( inv.handleRequest( request ) )
                {
                    // Forward request to local order stage
                    // If we already have the request but no reply yet, we have to order the request (again)
                    // (e.g. using read-only optimization or after a view change).

                    // Clients can resent requests in which case they have to be reordered. Thus, the whole window has to be
                    // considered and not only the next instance.
                    if( invono>m_readycmdhigh )
                        m_readycmdhigh = invono;
                    else if( invono-1<m_readycmdlow )
                        m_readycmdlow = invono-1;

                    s_logger.debug( "{} received new pending request {}", this, request );

                    notifyReady();
                }
                else
                {
                    s_logger.debug( "{} received request {} already proposed", this, request );
                }
            }
        }
    }

    protected abstract void verifyRequest(Request request) throws VerificationException;


    @Override
    public void enqueueRequestExecuted(RequestExecuted reqexecd)
    {
        Request request = reqexecd.getRequest();
        long    invno   = request.getNumber();

        if( m_invwnd.isBelowWindow( invno ) )
            s_logger.debug( "{} execution of request {} is outdated; window starts at {}", this, request, m_invwnd.getWindowStart() );
        else
            prepareInvocation( invno ).handleCommandExecuted( reqexecd );
    }


    @Override
    public void enqueueReply(Reply reply)
    {
        long invno = reply.getInvocationNumber();

        if( m_invwnd.isBelowWindow( invno ) )
            s_logger.debug( "{} reply {} is outdated; window starts at {}", this, reply, m_invwnd.getWindowStart() );
        else
        {
            s_logger.debug( "{} received reply {}", this, reply );

            prepareInvocation( invno ).handleReply( reply );
        }
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        while( m_cliconn.isReady() )
            m_cliconn.execute();

        return isDone( true );
    }


    @Override
    public Request pollPendingRequests()
    {
        while( m_readycmdlow<m_readycmdhigh )
        {
            Request req = m_invwnd.getSlotUnchecked( ++m_readycmdlow ).retrievePendingRequest();

            if( req!=null )
                return req;
        }

        return null;
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private BFTReplicaInstance prepareInvocation(long invno)
    {
        if( invno>=m_invwnd.getWindowEnd() )
        {
            for( long current = m_invwnd.forwardWindow( invno - m_invwnd.size() + 1 ); current<=invno; current++ )
                m_invwnd.getSlotUnchecked( current ).initInvocation( current );
        }

       if( m_readycmdlow<m_invwnd.getWindowStart()-1 )
           m_readycmdlow = m_invwnd.getWindowStart()-1;

        return m_invwnd.getSlot( invno );
    }

}
