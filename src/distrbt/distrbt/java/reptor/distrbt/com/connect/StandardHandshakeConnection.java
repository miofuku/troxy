package reptor.distrbt.com.connect;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.com.handshake.HandshakeRole;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.com.handshake.SocketConnectionArguments;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.DataChannelTask;
import reptor.distrbt.io.link.BufferedToUnbufferedLink;
import reptor.distrbt.io.link.UnbufferedToBufferedLink;
import reptor.distrbt.io.net.BufferedNetwork;


public class StandardHandshakeConnection<R>
        extends AbstractNetworkConnection<CommunicationSource, CommunicationSink>
        implements HandshakeHandler, TimerHandler, DataChannelContext<SelectorDomainContext>
{

    //-------------------------------------//
    //                Types                //
    //-------------------------------------//

    public enum Status
    {
        INITIATE,
        INITIATED,
        FINISH,
        FINISHED
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( StandardHandshakeConnection.class );

    private final short                     m_handlno;

    private final BufferedNetwork           m_net;

    private final Handshake<R>              m_handshake;

    private final DataChannelTask           m_inchannel;
    private final DataChannelTask           m_outchannel;

    private final SocketChannelConnector    m_conntr;
    private final TimeKey                   m_time;

    private HandshakeRole           m_role;
    private Status                  m_status;
    private InetSocketAddress       m_remaddr;
    private IOException             m_error;


    public StandardHandshakeConnection(SchedulerContext<? extends SelectorDomainContext> master, short handlno,
                                       BufferedNetwork net, Handshake<R> handshake)
    {
        m_handlno   = handlno;
        m_net       = net;
        m_handshake = handshake;

        bindToMaster( master );

        m_conntr = new SocketChannelConnector( this );
        m_time   = master.getDomainContext().registerTimer( this );

        m_inchannel = new BufferedToUnbufferedLink( m_net.getInbound(), m_handshake.getInboundConnect() );
        m_inchannel.bindToMaster( this );

        m_outchannel = new UnbufferedToBufferedLink( m_handshake.getOutboundConnect(), m_net.getOutbound() );
        m_outchannel.bindToMaster( this );

        if( s_logger.isDebugEnabled() )
            m_handshake.initLogChannel( this::log );

        clear( false );
    }


    protected SocketChannel channel()
    {
        return m_conntr.isUsed() ? m_conntr.getChannel() : m_net.getNetwork().getChannel();
    }


    //-------------------------------------//
    //              Properties             //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "HS%02d[%s]", m_handlno, master() );
    }


    @Override
    public short getHandlerNumber()
    {
        return m_handlno;
    }


    @Override
    public boolean isUnused()
    {
        return m_role==null;
    }


    @Override
    public boolean isConnector()
    {
        return m_role==HandshakeRole.CONNECTOR;
    }


    public SocketAddress getRemoteAddress()
    {
        return m_remaddr;
    }


    @Override
    public boolean isFinished()
    {
        return m_status==Status.FINISHED;
    }


    public R getRemote()
    {
        return m_handshake.getRemote();
    }


    @Override
    public HandshakeState getState()
    {
        return m_handshake.getState();
    }


    @Override
    public boolean hasError()
    {
        return m_error!=null;
    }


    @Override
    public IOException getError()
    {
        return m_error;
    }


    @Override
    public CommunicationSource getInbound()
    {
        return null;
    }


    @Override
    public CommunicationSink getOutbound()
    {
        return null;
    }


    //-------------------------------------//
    //            Configuration            //
    //-------------------------------------//

    @Override
    public void clear(boolean close)
    {
        reset( true, close );
    }


    protected void reset(boolean clear, boolean close)
    {
        m_handshake.reset( clear );

        m_time.clear();

        clearChannel( close );

        m_status = null;
        m_error  = null;

        if( clear )
        {
            m_role     = null;
            m_remaddr  = null;

            clearReady();
        }
    }


    private void clearChannel(boolean close)
    {
        boolean wasopen = m_conntr.isOpen() || m_net.isOpen();

        m_conntr.clear( close );
        m_net.clear( close );

        if( close && wasopen )
            log( "closed", "", null );
    }


    @Override
    public boolean connect(Object args)
    {
        assert m_role==null;

        SocketConnectionArguments ca = (SocketConnectionArguments) args;

        m_role    = HandshakeRole.CONNECTOR;
        m_remaddr = ca.getAddress();

        m_handshake.initConnection( args );

        return retryConnectNow();
    }


    @Override
    public boolean retryConnectNow()
    {
        assert m_role==HandshakeRole.CONNECTOR;

        try
        {
            prepareRetry();

            if( m_conntr.connect( m_remaddr ) )
                channelConnected();

            return true;
        }
        catch( IOException e )
        {
            handleError( e );
            return false;
        }
    }


    protected void prepareRetry()
    {
        reset( false, true );

        m_status = Status.INITIATE;
    }


    protected void channelConnected() throws IOException
    {
        markReady();

        m_net.init( m_conntr.getChannel(), m_conntr.getSelectionKey() );
        m_handshake.connect( m_remaddr );
        m_net.adjustBuffer( m_handshake );

        m_net.activate();
        m_handshake.activate();

        m_conntr.clear( false );

        advanceStatus( Status.INITIATED );
        log( "initiated", "", null );
    }


    @Override
    public boolean retryConnect(long delay)
    {
        assert m_role==HandshakeRole.CONNECTOR;

        log( "reconnect", " in {} ms", delay );

        prepareRetry();

        m_time.schedule( delay );

        return true;
    }


    @Override
    public boolean accept(SocketChannel channel)
    {
        assert m_role==null;

        try
        {
            m_role    = HandshakeRole.ACCEPTOR;
            m_error   = null;
            m_remaddr = (InetSocketAddress) channel.getRemoteAddress();

            channel.configureBlocking( false );
            m_net.init( channel, null );
            m_handshake.accept( m_remaddr );
            m_net.adjustBuffer( m_handshake );

            m_net.activate();
            m_handshake.activate();

            advanceStatus( Status.INITIATED );
            markReady();

            return true;
        }
        catch( IOException e )
        {
            handleError( e );
            return false;
        }
    }


    @Override
    public String getChannelName()
    {
        return toString();
    }


    //-------------------------------------//
    //                Actor                //
    //-------------------------------------//

    @Override
    public void timeElapsed(TimeKey key)
    {
        log( "timer elapsed", "", null );

        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( m_status==Status.FINISHED )
            return true;

        try
        {
            if( m_status==Status.INITIATE )
                return isDone( executeConnect() );
            else
            {
                boolean isdone = executeChannels( m_outchannel, m_inchannel );

                // Wait until all remaining channel operations have been carried out.
                if( m_status!=Status.FINISH && m_handshake.isFinished() && !isdone )
                {
                    m_net.getInbound().deactivate();
                    advanceStatus( Status.FINISH );
                    log( "finishing", "", null );
                }
                else if( ( m_status==Status.FINISH || m_handshake.isFinished() ) && isdone )
                {
                    // Log before finish() since finish() could clear the network handler.
                    if( isConnector() )
                        log( "established", "", null );
                    else
                        log( "accepted", "", null );

                    m_net.deactivate();

                    m_handshake.saveState( m_net.initiateMigration(), null );

                    advanceStatus( Status.FINISHED );
                }
                else if( m_handshake.needsReconfiguraiton() )
                {
                    m_handshake.reconfigure();

                    m_net.adjustBuffer( m_handshake );

                    isdone = false;
                }

                return isDone( isdone );
            }
        }
        catch( IOException e )
        {
            handleError( e );

            return isDone( true );
        }

    }


    private boolean executeConnect() throws IOException
    {
        boolean isdone = m_conntr.isUsed() ? m_conntr.execute() : m_conntr.connect( m_remaddr );

        if( m_conntr.hasError() )
            throw m_conntr.getError();
        else if( !m_conntr.isConnected() )
            return isdone;
        else
        {
            channelConnected();

            return false;
        }
    }


    private void handleError(IOException e)
    {
        log( "error", ": {}", e );

        m_error = e;
        advanceStatus( Status.FINISHED );
    }


    private void advanceStatus(StandardHandshakeConnection.Status status)
    {
        m_status = status;
    }


    // TODO: Move to log4j
    protected void log(String action, String msg, Object arg)
    {
        if( !s_logger.isDebugEnabled() )
            return;

        assert m_role!=null;

        if( arg instanceof Exception )
            arg = arg.toString();

        s_logger.debug( "{} {} {} {}" + msg, toString(), action, m_handshake.getConnectionDescription(), channel(), arg );
    }


    protected void logInfo(String action, String msg, Object arg)
    {
        if( !s_logger.isInfoEnabled() )
            return;

        assert m_role!=null;

        if( arg instanceof Exception )
            arg = arg.toString();

        s_logger.info( "{} {} {} {}" + msg, toString(), action, m_handshake.getConnectionDescription(), channel(), arg );
    }

}
