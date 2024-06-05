package reptor.distrbt.io.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.SynchronousSink;
import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;


// Network endpoints are state-triggered and not event-triggered data link elements. As long as their is something
// to do they will propagate their ready state. This could be generalized if needed.
public class Network implements CommunicationLayer<CommunicationSink, CommunicationSource, SynchronousSource, SynchronousSink>,
                                ChannelHandler
{

    private static final Logger s_logger = LoggerFactory.getLogger( Network.class );

    private final SelectorDomainContext m_domcntxt;
    private final ConnectionObserver    m_observer;

    private final NetworkSource         m_source = new NetworkSource();
    private NetworkEndpointContext<? extends SelectorDomainContext> m_sourcemaster;
    private final NetworkSink           m_sink   = new NetworkSink();
    private NetworkEndpointContext<? extends SelectorDomainContext> m_sinkmaster;

    private SocketChannel               m_channel  = null;
    private SelectionKey                m_selkey   = null;


    public Network(SelectorDomainContext domcntxt, ConnectionObserver observer)
    {
        m_domcntxt = Objects.requireNonNull( domcntxt );
        m_observer = Objects.requireNonNull( observer );
    }


    @Override
    public NetworkSource getInboundStage()
    {
        return m_source;
    }


    @Override
    public NetworkSink getOutboundStage()
    {
        return m_sink;
    }


    @Override
    public NetworkSource getInbound()
    {
        return m_source;
    }


    @Override
    public NetworkSink getOutbound()
    {
        return m_sink;
    }


    @Override
    public CommunicationSink getInboundConnect()
    {
        return null;
    }


    @Override
    public CommunicationSource getOutboundConnect()
    {
        return null;
    };


    public SocketChannel getChannel()
    {
        return m_channel;
    }


    public SelectionKey getSelectionKey()
    {
        return m_selkey;
    }


    public void init(SocketChannel channel, SelectionKey selkey) throws IOException
    {
        Preconditions.checkState( !isInitialized() );

        m_channel = Objects.requireNonNull( channel );

        if( selkey==null )
            m_selkey = m_domcntxt.registerChannel( this, m_channel, 0 );
        else
            m_selkey = m_domcntxt.migrateRegisteredChannel( selkey, this );

        m_observer.connectionInitialised( channel );
    }


    @Notifying
    public void open(SocketChannel channel, SelectionKey selkey) throws IOException
    {
        init( channel, selkey );
        activate();
    }


    public void close()
    {
        if( !isOpen()  )
            return;

        s_logger.debug( "close {}", m_channel );

        deactivate();

        m_selkey.cancel();

        try
        {
            m_channel.close();
        }
        catch( IOException e )
        {
        }
    }


    public boolean isOpen()
    {
        return m_channel!=null && m_channel.isOpen();
    }


    public void clear(boolean close)
    {
        if( m_channel==null )
            return;

        deactivate();

        if( close )
            close();

        m_selkey   = null;
        m_channel  = null;
    }


    public boolean isInitialized()
    {
        return m_channel!=null;
    }


    public void activate()
    {
        m_source.activate();
        m_sink.activate();
    }


    public void deactivate()
    {
        m_source.deactivate();
        m_sink.deactivate();
    }


    public boolean isActivated()
    {
        return m_source.isActivated() || m_sink.isActivated();
    }


    public NetworkState initiateMigration()
    {
        Preconditions.checkState( isInitialized() );

        if( m_selkey!=null )
            m_domcntxt.prepareMigrationOfRegisteredChannel( m_selkey );

        NetworkState state = new NetworkState( m_channel, m_selkey );

        clear( false );

        return state;
    }


    public void installState(NetworkState state) throws IOException
    {
        init( state.getChannel(), state.getSelectionKey() );
    }


    @Override
    public void channelReady(SelectionKey key)
    {
        if( key.isReadable() )
            m_sourcemaster.dataReady( m_source );

        if( key.isWritable() )
            m_sinkmaster.dataReady( m_sink );
    }


    private abstract class AbstractNetworkEndpoint implements NetworkEndpoint
    {
        protected NetworkEndpointContext<? extends SelectorDomainContext> m_master;

        protected boolean m_isactivated = false; // m_isactivated -> isInitilized() && master!=null

        @Override
        public void bindToMaster(NetworkEndpointContext<? extends SelectorDomainContext> master)
        {
            Preconditions.checkState( m_master==null );
            Preconditions.checkArgument( master.getDomainContext().getDomainAddress()==m_domcntxt.getDomainAddress()  );
            Preconditions.checkState( !m_isactivated );

            m_master = Objects.requireNonNull( master );
        }

        @Override
        public void unbindFromMaster(NetworkEndpointContext<? extends SelectorDomainContext> master)
        {
            Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );
            Preconditions.checkState( !m_isactivated );

            m_master = null;
        }

        @Override
        @Notifying
        public void activate()
        {
            if( m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            Preconditions.checkState( m_master!=null );

            m_isactivated = true;
            m_master.endpointActivated( this );
        }

        @Override
        public void deactivate()
        {
            if( !m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            Preconditions.checkState( m_master!=null );

            disable();
            m_isactivated = false;
        }

        @Override
        public boolean isActivated()
        {
            return m_isactivated;
        }

        protected void enableOps(int ops)
        {
            if( m_isactivated )
                m_selkey.interestOps( m_selkey.interestOps() | ops );
        }

        protected void disableOps(int ops)
        {
            if( m_isactivated )
                m_selkey.interestOps( m_selkey.interestOps() & ~ops );
        }
    }



    public final class NetworkSource extends AbstractNetworkEndpoint implements SynchronousSource
    {
        @Override
        public SynchronousSink getSink()
        {
            return null;
        }

        @Override
        public SynchronousSource getSource()
        {
            return this;
        }

        @Override
        public void bindToMaster(NetworkEndpointContext<? extends SelectorDomainContext> master)
        {
            super.bindToMaster( master );

            m_sourcemaster = master;
        }

        @Override
        public void enable()
        {
            enableOps( SelectionKey.OP_READ );
        }

        @Override
        public void disable()
        {
            disableOps( SelectionKey.OP_READ );
        }

        @Override
        public boolean isReady()
        {
            return m_isactivated && m_selkey.isReadable();
        }

        public int retrieveData(ByteBuffer dst) throws IOException
        {
            if( !m_isactivated )
                return 0;

            int nbytes = m_channel.read( dst );

            m_observer.dataReceived( nbytes );

            return nbytes;
        }
    };


    public class NetworkSink extends AbstractNetworkEndpoint implements SynchronousSink
    {
        @Override
        public SynchronousSink getSink()
        {
            return this;
        }

        @Override
        public SynchronousSource getSource()
        {
            return null;
        }

        @Override
        public void bindToMaster(NetworkEndpointContext<? extends SelectorDomainContext> master)
        {
            super.bindToMaster( master );

            m_sinkmaster = master;
        }

        @Override
        public void enable()
        {
            enableOps( SelectionKey.OP_WRITE );
        }

        @Override
        public void disable()
        {
            disableOps( SelectionKey.OP_WRITE );
        }

        @Override
        public boolean isReady()
        {
            return m_isactivated && m_selkey.isWritable();
        }

        public boolean canProcessData()
        {
            return isReady();
        }

        public int processData(ByteBuffer src) throws IOException
        {
            if( !m_isactivated )
                return 0;

            int nbytes = m_channel.write( src );

            m_observer.dataSent( nbytes );

            return nbytes;
        }
    }

}