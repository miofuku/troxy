package reptor.tbft;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import reptor.distrbt.com.handshake.HandshakeRole;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.connect.StandardConnectionArguments;
import reptor.replct.connect.StandardHandshakeState;
import reptor.replct.invoke.ClientToWorkerAssignment;


public class ClientAssignment implements Handshake<RemoteEndpoint>
{

    private final byte                      m_repno;
    private final AtomicInteger             m_clictr;
    private final ClientToWorkerAssignment  m_clitowrk;

    private HandshakeRole                   m_role;
    private RemoteEndpoint                  m_remote;
    private StandardHandshakeState          m_hsstate;

    private final Inbound  m_inbound  = new Inbound();
    private final Outbound m_outbound = new Outbound();

    private LogChannel                      m_logchannel;


    public ClientAssignment(byte repno, AtomicInteger clictr, ClientToWorkerAssignment clitowrk)
    {
        m_repno     = repno;
        m_clictr    = Objects.requireNonNull( clictr );
        m_clitowrk  = Objects.requireNonNull( clitowrk );
    }


    @Override
    public UnbufferedDataSink getInboundConnect()
    {
        return m_inbound;
    }


    @Override
    public UnbufferedDataSource getOutboundConnect()
    {
        return m_outbound;
    }


    @Override
    public void initLogChannel(LogChannel logchannel)
    {
        m_logchannel = logchannel;
    }


    @Override
    public void activate()
    {
    }


    @Override
    public void deactivate()
    {
    }


    @Override
    public boolean isActivated()
    {
        return false;
    }


    @Override
    public void reset(boolean clear)
    {
        m_hsstate = null;

        if( clear )
        {
            m_role   = null;
            m_remote = null;
        }
    }


    @Override
    public void initConnection(Object args)
    {
        assert m_role==null;

        m_role   = HandshakeRole.CONNECTOR;
        m_remote = ((StandardConnectionArguments) args).getRemoteEndpoint();
    }


    @Override
    public void connect(InetSocketAddress remaddr) throws IOException
    {
        assert m_role==HandshakeRole.CONNECTOR;
    }


    @Override
    public void accept(InetSocketAddress remaddr) throws IOException
    {
        assert m_role==null;

        m_role = HandshakeRole.ACCEPTOR;

        short cliseqno = (short) m_clictr.getAndIncrement();
        short clino    = m_clitowrk.getClientForLocalSequence( m_repno, cliseqno );

        m_remote = new RemoteEndpoint( clino, (short) 0 );

        if( m_logchannel!=null )
            m_logchannel.log( "assigned", ": client {}", cliseqno );
    }


    @Override
    public boolean needsReconfiguraiton()
    {
        return false;
    }


    @Override
    public void reconfigure()
    {
    }


    @Override
    public RemoteEndpoint getRemote()
    {
        return m_remote;
    }


    @Override
    public boolean isFinished()
    {
        return m_remote!=null;
    }


    @Override
    public void saveState(BufferedNetworkState netstate, SslState sslstate)
    {
        m_hsstate = new StandardHandshakeState( m_remote, m_role==HandshakeRole.CONNECTOR, netstate, sslstate );
    }


    @Override
    public HandshakeState getState()
    {
        return m_hsstate;
    }


    @Override
    public String getConnectionDescription()
    {
        short remno = m_remote==null ? -1 : m_remote.getProcessNumber();
        short netno = m_remote==null ? -1 : m_remote.getNetworkNumber();

        return m_role==HandshakeRole.CONNECTOR ? String.format( "%d -> %d (%d)", m_repno, remno, netno ) :
                                                 String.format( "%d <- %d (%d)", remno, m_repno, netno );
    }


    private static class Inbound implements UnbufferedDataSink
    {
        @Override
        public int getMinimumBufferSize()
        {
            return 0;
        }

        @Override
        public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
        }

        @Override
        public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
        }

        @Override
        public boolean isReady()
        {
            return false;
        }

        @Override
        public UnbufferedDataSinkStatus canProcessData()
        {
            return UnbufferedDataSinkStatus.BLOCKED;
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
        }
    }


    private static class Outbound implements UnbufferedDataSource
    {
        @Override
        public int getMinimumBufferSize()
        {
            return 0;
        }

        @Override
        public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
        }

        @Override
        public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
        }

        @Override
        public boolean isReady()
        {
            return false;
        }

        @Override
        public int getRequiredBufferSize()
        {
            return NO_PENDING_DATA;
        }

        @Override
        public boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return false;
        }

        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
        }
    }
}
