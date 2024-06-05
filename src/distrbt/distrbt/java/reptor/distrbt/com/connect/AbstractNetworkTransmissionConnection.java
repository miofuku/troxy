package reptor.distrbt.com.connect;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.NetworkTransmissionConnection;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.DataChannelTask;
import reptor.distrbt.io.net.Network;
import reptor.jlib.NotImplementedException;


public abstract class AbstractNetworkTransmissionConnection<I extends CommunicationSource, O extends CommunicationSink>
        extends AbstractNetworkConnection<I, O>
        implements NetworkTransmissionConnection<I, O>, DataChannelContext<SelectorDomainContext>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( AbstractNetworkTransmissionConnection.class );

    private final int                           m_connid;
    private final SocketChannelConfiguration    m_config;


    public AbstractNetworkTransmissionConnection(int connid, SocketChannelConfiguration config)
    {
        m_connid = connid;
        m_config = config;
    }


    protected abstract Network         net();


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "CONN[%05d]", m_connid );
    }


    @Override
    public boolean isOpen()
    {
        return net().isOpen();
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    @Override
    public void bindToMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        super.bindToMaster( master );
    }


    @Override
    public void unbindFromMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        super.unbindFromMaster( master );
    }


    @Override
    public void open(HandshakeState hsstate)
    {
        Preconditions.checkState( !net().isOpen() );

        try
        {
            m_config.configureChannel( hsstate.getBufferedNetworkState().getNetworkState().getChannel() );

            doOpen( hsstate );
        }
        catch( IOException e )
        {
            s_logger.error( "{} error while opening channel:", this, e );

            // We have only a partially initialized state that's why we don't leave the invocation of close()
            // to the caller.
            close();

            throw new NotImplementedException( e );
        }
    }


    protected abstract void doOpen(HandshakeState hsstate) throws IOException;


    private void close()
    {
        if( !net().isOpen() )
            return;

        net().close();

        clearReady();

        s_logger.debug( "{} closed", this );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    protected boolean executeChannelsCatched(DataChannelTask first, DataChannelTask second)
    {
        assert isReady();

        try
        {
            return isDone( executeChannels( first, second ) );
        }
        catch( IOException e )
        {
            if( e.getCause()==null )
                s_logger.warn( "{} " + e.toString(), this );
            else
            {
                StringWriter sw = new StringWriter();
                sw.append( "{} " );
                e.printStackTrace( new PrintWriter( sw ) );
                s_logger.warn( sw.toString(), this );
            }

            close();

            return isDone( true );
        }
    }


    //-------------------------------------//
    //            Master Context           //
    //-------------------------------------//

    @Override
    public String getChannelName()
    {
        return toString();
    }

}