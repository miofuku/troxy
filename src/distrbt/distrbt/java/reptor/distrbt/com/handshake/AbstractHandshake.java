package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import reptor.chronos.Notifying;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;
import reptor.distrbt.io.stage.AbstractCommunicationLayerElement;


public abstract class AbstractHandshake<R> implements Handshake<R>

{

    private final Inbound       m_inbound  = new Inbound();
    private final Outbound      m_outbound = new Outbound();

    private LogChannel          m_logchannel;
    private HandshakeRole       m_role;
    private boolean             m_isfinished;


    protected HandshakeRole role()
    {
        return m_role;
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
        m_inbound.activate();
        m_outbound.activate();
    }


    @Override
    public void deactivate()
    {
        m_inbound.deactivate();
        m_outbound.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_inbound.isActivated() || m_outbound.isActivated();
    }


    @Override
    public void reset(boolean clear)
    {
        m_inbound.clear();
        m_outbound.clear();

        if( clear )
        {
            m_role       = null;
            m_isfinished = false;
        }
    }


    @Override
    public void initConnection(Object args)
    {
        assert m_role==null;

        m_role = HandshakeRole.CONNECTOR;
    }


    @Override
    public void connect(InetSocketAddress remaddr) throws IOException
    {
        assert m_role==HandshakeRole.CONNECTOR;

        executeConnectorPhases( null );
    }


    @Override
    public void accept(InetSocketAddress remaddr) throws IOException
    {
        assert m_role==null;

        m_role = HandshakeRole.ACCEPTOR;

        executeAcceptorPhases( null );
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
    public boolean isFinished()
    {
        return m_isfinished;
    }


    @Override
    public void saveState(BufferedNetworkState netstate, SslState sslstate)
    {
    }


    @Override
    public abstract HandshakeState getState();



    @Override
    public abstract String getConnectionDescription();



    private class Inbound extends AbstractCommunicationLayerElement implements UnbufferedDataSink
    {
        private UnbufferedDataSinkStatus    m_sinkstatus = UnbufferedDataSinkStatus.BLOCKED;
        private int                         m_reqbytes   = UnbufferedDataSource.NO_PENDING_DATA;

        @Override
        public int getMinimumBufferSize()
        {
            return AbstractHandshake.this.getMinimumBufferSize();
        }

        @Notifying
        public void prepareIncoming(int nbytes)
        {
            assert m_sinkstatus==UnbufferedDataSinkStatus.BLOCKED;

            m_reqbytes = nbytes;

            if( isActivated() )
                enable();
        }

        @Override
        public void activate()
        {
            super.activate();

            if( m_reqbytes!=UnbufferedDataSource.NO_PENDING_DATA )
                enable();
        }

        public void clear()
        {
            reset();
            deactivate();
        }

        @Notifying
        private void enable()
        {
            m_sinkstatus = UnbufferedDataSinkStatus.CAN_PROCESS;

            notifyReady();
        }

        @Override
        protected void disable()
        {
            m_sinkstatus = UnbufferedDataSinkStatus.BLOCKED;

            super.disable();
        }

        private void reset()
        {
            m_reqbytes = UnbufferedDataSource.NO_PENDING_DATA;
        }

        @Override
        public UnbufferedDataSinkStatus canProcessData()
        {
            return m_sinkstatus;
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
            if( m_sinkstatus==UnbufferedDataSinkStatus.BLOCKED )
                return;

            if( src.remaining()<m_reqbytes )
            {
                m_sinkstatus = UnbufferedDataSinkStatus.WAIT_FOR_DATA;
                clearReady();
            }
            else
            {
                disable();
                incomingReady( src );
            }
        }
    }


    private class Outbound extends AbstractCommunicationLayerElement implements UnbufferedDataSource
    {
        private boolean m_isenabled = false;
        private int     m_reqbytes  = NO_PENDING_DATA;

        @Override
        public int getMinimumBufferSize()
        {
            return AbstractHandshake.this.getMinimumBufferSize();
        }

        @Notifying
        public void prepareOutgoing(int nbytes)
        {
            assert m_reqbytes==NO_PENDING_DATA;

            m_reqbytes = nbytes;

            if( isActivated() )
                enable();
        }

        @Override
        public void activate()
        {
            super.activate();

            if( m_reqbytes!=NO_PENDING_DATA )
                enable();
        }

        public void clear()
        {
            reset();
            deactivate();
        }

        @Notifying
        private void enable()
        {
            m_isenabled = true;

            notifyReady();
        }

        @Override
        protected void disable()
        {
            m_isenabled = false;

            super.disable();
        }

        private void reset()
        {
            m_reqbytes = NO_PENDING_DATA;
        }

        @Override
        public int getRequiredBufferSize()
        {
            return m_isenabled ? m_reqbytes : NO_PENDING_DATA;
        }

        @Override
        public boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return m_isenabled && bufsize>=m_reqbytes;
        }

        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
            if( !canRetrieveData( false, dst.remaining() ) )
                clearReady();
            else
            {
                disable();
                outgoingReady( dst );
            }
        }
    }


    protected abstract int getMinimumBufferSize();


    protected void prepareIncoming(int nbytes)
    {
        m_inbound.prepareIncoming( nbytes );
    }


    protected void incomingReady(ByteBuffer src) throws IOException
    {
        bufferReady( src );
    }


    protected void prepareOutgoing(int nbytes)
    {
        m_outbound.prepareOutgoing( nbytes );
    }


    protected void outgoingReady(ByteBuffer dst) throws IOException
    {
        bufferReady( dst );
    }


    protected void bufferReady(ByteBuffer buffer) throws IOException
    {
        m_isfinished = m_role==HandshakeRole.CONNECTOR ? executeConnectorPhases( buffer ) : executeAcceptorPhases( buffer );
    }


    protected abstract boolean  executeConnectorPhases(ByteBuffer buffer) throws IOException;

    protected abstract boolean  executeAcceptorPhases(ByteBuffer buffer) throws IOException;


    protected void log(String action, String msg, Object arg)
    {
        if( m_logchannel!=null )
            m_logchannel.log( action, msg, arg );
    }

}
