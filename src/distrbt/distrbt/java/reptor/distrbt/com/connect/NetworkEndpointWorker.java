package reptor.distrbt.com.connect;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;

// That's not an acceptor since an acceptor would be distinct from a connector but this entity is devised from it.
public class NetworkEndpointWorker extends NetworkHandshakeWorker implements ChannelHandler
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( NetworkEndpointWorker.class );

    private ServerSocketChannel m_channel;
    private SelectionKey        m_selkey;


    public NetworkEndpointWorker(SchedulerContext<? extends SelectorDomainContext> master, String name, short epno, int nhandlers,
                                 HandshakeHandlerFactory handlfac, int recondelay)
    {
        super( master, name, epno, nhandlers, handlfac, recondelay );
    }


    //-------------------------------------//
    //             Properties              //
    //-------------------------------------//

    public boolean isOpen()
    {
        return m_channel!=null;
    }


    //-------------------------------------//
    //            Configuration            //
    //-------------------------------------//

    public void open(SocketAddress addr)
    {
        Preconditions.checkState( m_channel==null );

        try
        {
            m_channel = ServerSocketChannel.open();
            m_channel.configureBlocking( false );
            m_channel.bind( addr );

            m_selkey = master().getDomainContext().registerChannel( this, m_channel, SelectionKey.OP_ACCEPT );

            s_logger.debug( "{} listen to {}", this, addr );
        }
        catch( IOException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    public void close()
    {
        try
        {
            m_channel.close();
        }
        catch( IOException e )
        {
        }

        m_channel = null;
        m_selkey  = null;

        clearReady();
    }


    //-------------------------------------//
    //                Actor                //
    //-------------------------------------//

    @Override
    public void channelReady(SelectionKey key)
    {
        if( isOpen() )
            notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( m_selkey.isAcceptable() )
            processAccepts();

        processHandlers();

        updateInterest();

        return isDone( true );
    }


    private void processAccepts()
    {
        try
        {
            while( !unusedHandlers().isEmpty() )
            {
                SocketChannel channel = m_channel.accept();

                if( channel==null )
                    break;

                s_logger.debug( "{} new connection {}", toString(), channel );

                HandshakeHandler handler = unusedHandlers().poll();

                handler.accept( channel );

                if( handler.isReady() )
                    markHandlerReady( handler );
            }
        }
        catch( IOException e )
        {
            throw new NotImplementedException( e );
        }
    }


    private void updateInterest()
    {
        if( unusedHandlers().isEmpty() )
        {
            if( m_selkey.interestOps()!=0 )
                m_selkey.interestOps( 0 );
        }
        else
        {
            if( m_selkey.interestOps()==0 )
                m_selkey.interestOps( SelectionKey.OP_ACCEPT );
        }
    }


    @Override
    protected void handlersAvailable()
    {
        super.handlersAvailable();

        updateInterest();
    }

}
