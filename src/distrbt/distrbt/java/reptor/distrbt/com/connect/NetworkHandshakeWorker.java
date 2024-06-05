package reptor.distrbt.com.connect;

import java.util.ArrayDeque;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.Asynchronous;
import reptor.chronos.ChronosTask;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
import reptor.jlib.collect.Slots;


public class NetworkHandshakeWorker extends AbstractMaster<SelectorDomainContext> implements Actor
{

    //-------------------------------------//
    //                Types                //
    //-------------------------------------//

    public interface HandshakeHandlerFactory
    {
        Slots<? extends HandshakeHandler>
            createHandshakeHandlers(SchedulerContext<? extends SelectorDomainContext> cntxt, short epno, int nhandlers);
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( NetworkHandshakeWorker.class );

    private final SchedulerContext<? extends SelectorDomainContext> m_cntxt;

    private final String                            m_name;

    private final Slots<? extends HandshakeHandler> m_handlers;
    private final Queue<HandshakeHandler>           m_unused;
    private final Queue<HandshakeHandler>           m_finished;
    private final Queue<HandshakeHandler>           m_ready;
    private final Queue<Object>                     m_backlog;

    private final int   m_recondelay;


    public NetworkHandshakeWorker(SchedulerContext<? extends SelectorDomainContext> cntxt, String name,
                                  short epno, int nhandlers, HandshakeHandlerFactory handlfac, int recondelay)
    {
        m_cntxt      = cntxt;
        m_name       = name;
        m_backlog    = new ArrayDeque<>( 1 );
        m_recondelay = recondelay;

        // Initialize m_ready before m_handlers since this is exposed.
        m_finished = new ArrayDeque<>( nhandlers );
        m_ready    = new ArrayDeque<>( nhandlers );
        m_handlers = handlfac.createHandshakeHandlers( this, epno, nhandlers );
        m_unused   = new ArrayDeque<>( m_handlers );
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    protected Queue<HandshakeHandler> unusedHandlers()
    {
        return m_unused;
    }


    //-------------------------------------//
    //              Properties             //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return m_name;
    }


    //-------------------------------------//
    //                 Actor               //
    //-------------------------------------//

    @Asynchronous
    public void startConnection(Object args)
    {
        s_logger.debug( "start connection {}", args );

        if( m_unused.isEmpty() )
            m_backlog.add( args );
        else
        {
            connect( args );
            notifyReady();
        }
    }


    @Override
    public boolean execute()
    {
        processHandlers();

        return isDone( true );
    }


    protected void processHandlers()
    {
        processBacklog();

        processReadyList();
    }


    private void processBacklog()
    {
        while( !m_backlog.isEmpty() && !m_unused.isEmpty() )
        {
            Object job = m_backlog.poll();

            connect( job );
        }
    }


    private void processReadyList()
    {
        HandshakeHandler handler;

        while( ( handler = m_ready.poll() )!=null )
        {
            if( !handler.execute() )
                markHandlerReady( handler );
            else if( handler.isFinished() )
            {
                if( !handler.hasError() )
                    m_finished.add( handler );
                else if( !handler.isConnector() )
                    handler.clear( true );
                else if( !handler.retryConnect( m_recondelay ) )
                    throw new NotImplementedException( handler.getError() );
            }
        }
    }


    //-------------------------------------//
    //               Results               //
    //-------------------------------------//

    public HandshakeState pollNewConnections()
    {
        HandshakeHandler handler = m_finished.poll();

        if( handler==null )
            return null;

        HandshakeState hsstate = handler.getState();

        handler.clear( false );
        markHandlerUnused( handler );

        return hsstate;
    }


    //-------------------------------------//
    //                Master               //
    //-------------------------------------//

    @Override
    public void taskReady(ChronosTask task)
    {
        markHandlerReady( (HandshakeHandler) task );

        notifyReady();
    }


    //-------------------------------------//
    //             Auxiliaries             //
    //-------------------------------------//

    private void connect(Object args)
    {
        HandshakeHandler handler = m_unused.poll();

        if( !handler.connect( args ) )
            throw new NotImplementedException( handler.getError() );

        if( handler.isReady() )
            markHandlerReady( handler );
    }


    protected void markHandlerReady(HandshakeHandler handler)
    {
        assert handler.isReady();

        m_ready.add( handler );
    }


    protected void markHandlerUnused(HandshakeHandler handler)
    {
        m_unused.add( handler );

        if( m_unused.size()==1 )
            handlersAvailable();
    }


    protected void handlersAvailable()
    {
        if( !m_backlog.isEmpty() )
            markReady();
    }

}
