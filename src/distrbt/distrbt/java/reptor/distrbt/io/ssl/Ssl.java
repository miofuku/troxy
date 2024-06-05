package reptor.distrbt.io.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.chronos.context.ChronosDomainContext;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.CompactingSinkBuffers;
import reptor.distrbt.io.CompactingSourceBuffers;
import reptor.distrbt.io.ExternalizableDataElement;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.stage.AbstractAdaptiveToUnbufferedDataStage;
import reptor.distrbt.io.stage.AbstractUnbufferedToBufferedDataStage;
import reptor.jlib.NotImplementedException;


// An Ssl is not an orphic but time-transcendent. It represents a subdomain and mediates between its
// inbound and outbound incarnation which share an SSL session as volatile reference.
// Maybe it's a protocol stack layer.
// TODO: Implement proper closing. Is it ensured that the handshaking status is reset when the status is closed?
public class Ssl implements CommunicationLayer<UnbufferedDataSink, UnbufferedDataSource, BufferedDataSource, BufferedDataSink>
{

    private static final Logger s_logger = LoggerFactory.getLogger( Ssl.class );

    private final SelectorDomainContext m_domcntxt;

    private final SslInbound    m_inbound;
    private final SslOutbound   m_outbound;

    private final SSLContext    m_sslcntxt;
    private SSLEngine           m_sslengine;


    public Ssl(SelectorDomainContext domcntxt, SSLContext sslcntxt)
    {
        m_domcntxt = Objects.requireNonNull( domcntxt );
        m_sslcntxt = sslcntxt;

        m_inbound = new SslInbound();
        m_outbound = new SslOutbound();
    }


    @Override
    public SslInbound getInboundStage()
    {
        return m_inbound;
    }


    @Override
    public SslOutbound getOutboundStage()
    {
        return m_outbound;
    }


    @Override
    public UnbufferedDataSink getInboundConnect()
    {
        return m_inbound.getSink();
    }


    @Override
    public BufferedDataSource getInbound()
    {
        return m_inbound.getSource();
    }


    @Override
    public UnbufferedDataSource getOutboundConnect()
    {
        return m_outbound.getSource();
    }


    @Override
    public BufferedDataSink getOutbound()
    {
        return m_outbound.getSink();
    }


    public void adjustBuffer(ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> conn)
    {
        adjustBuffer( conn.getInboundConnect().getMinimumBufferSize(), conn.getOutboundConnect().getMinimumBufferSize() );
    }


    public void adjustBuffer(int mininbufsize, int minoutbufsize)
    {
        m_inbound.adjustBuffer( mininbufsize, 0 );
        m_outbound.adjustBuffer( minoutbufsize, 0 );
    }


    private void installEngine(SSLEngine sslengine)
    {
        if( isInitialized() )
            throw new IllegalStateException();

        m_sslengine = sslengine;

        int appbufsize = m_sslengine.getSession().getApplicationBufferSize();
        int netbufsize = m_sslengine.getSession().getPacketBufferSize();
        m_inbound.adjustBuffer( appbufsize, netbufsize );
        m_outbound.adjustBuffer( appbufsize, netbufsize );
    }


    public void init(InetSocketAddress addr, boolean clientmode)
    {
        Preconditions.checkState( m_sslcntxt!=null );

        try
        {
            SSLEngine sslengine = m_sslcntxt.createSSLEngine( addr.getHostName(), addr.getPort() );

            sslengine.setUseClientMode( clientmode );
            sslengine.beginHandshake();

            installEngine( sslengine );
        }
        catch( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Notifying
    public void open(InetSocketAddress addr, boolean clientmode)
    {
        init( addr, clientmode );
        activate();
    }


    public void clear()
    {
        m_inbound.clear();
        m_outbound.clear();

        m_sslengine = null;
    }


    public boolean isInitialized()
    {
        return m_sslengine!=null;
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


    public SslState initiateMigration()
    {
        SslState state = new SslState( m_sslengine, m_outbound.saveState(), m_inbound.saveState() );

        clear();

        return state;
    }


    public void installState(SslState state)
    {
        installEngine( state.getEngine() );

        m_outbound.installStateIfRequired( state.getOutboundState() );
        m_inbound.installStateIfRequired( state.getInboundState() );
    }


    public class SslInbound extends AbstractUnbufferedToBufferedDataStage implements ExternalizableDataElement
    {
        private ByteBuffer                  m_buffer;
        private int                         m_mininbufsize = 0;

        // Generally activated or not.
        private boolean                     m_isactivated   = false; // activated -> isOpen() && sinkMaster!=null && sourceMaster!=null
        // If a handshake is ongoing and an outgoing message is needed, the sink is (internally) disabled
        private boolean                     m_issinkenabled = true; // enabled -> activated
        private UnbufferedDataSinkStatus    m_sinkstatus    = UnbufferedDataSinkStatus.BLOCKED; // !BLOCKED (externally) -> enabled
        // Marked as ready
        private boolean                     m_issinkready   = false; // ready -> !BLOCKED
        private boolean                     m_issourceready = false;

        protected void adjustBuffer(int minappbufsize, int mininbufsize)
        {
            adjustSourceBuffer( minappbufsize );
            m_mininbufsize = mininbufsize;
        }

        @Override
        protected void adjustSourceBuffer(int minbufsize)
        {
            m_buffer = Ssl.this.adjustBuffer( m_buffer, minbufsize );
            CompactingSinkBuffers.clear( m_buffer );
        }

        @Override
        protected ChronosDomainContext domainContext()
        {
            return m_domcntxt;
        }

        @Override
        public String toString()
        {
            return channelName() + "[SSL(I)]";
        }

        @Override
        public int getStateSize()
        {
            return m_buffer.position();
        }

        @Override
        public void saveState(ByteBuffer dst)
        {
            CompactingSinkBuffers.saveState( m_buffer, dst );
        }

        @Override
        public void installState(ByteBuffer src)
        {
            Preconditions.checkState( !isActivated() );

            CompactingSinkBuffers.installState( m_buffer, src );
        }

        public boolean isBoundToMaster()
        {
            return sinkMaster()!=null && sourceMaster()!=null;
        }

        @Override
        @Notifying
        public void activate()
        {
            if( m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            Preconditions.checkState( sinkMaster()!=null && sourceMaster()!=null );

            m_isactivated = true;

            switch( m_sslengine.getHandshakeStatus() )
            {
            case NOT_HANDSHAKING:
                // We are optimistic that m_buffer.hasRemaining()
                enableSink();
                break;
            case NEED_UNWRAP:
                needIncomingHandshakeMessage();
                break;
            case NEED_WRAP:
                break;
           default:
                throw new NotImplementedException( m_sslengine.getHandshakeStatus().toString() );
            }
        }

        @Override
        public void deactivate()
        {
            if( !m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            assert sinkMaster()!=null && sourceMaster()!=null;

            disableSink();
            m_issourceready = false;
            m_isactivated   = false;
        }

        @Override
        public boolean isActivated()
        {
            return m_isactivated;
        }

        public void clear()
        {
            CompactingSinkBuffers.clear( m_buffer );

            disableSink();
            m_issourceready = false;
            m_isactivated   = false;
        }

        // The sink part:
        @Notifying
        private void enableSink()
        {
            assert m_isactivated;

            m_issinkenabled = true;
            // We are optimistic that m_buffer.hasRemaining()
            unblockSink();
        }

        private void disableSink()
        {
            blockSink();
            m_issinkenabled = false;
        }

        private void unblockSink()
        {
            m_sinkstatus = UnbufferedDataSinkStatus.CAN_PROCESS;

            notifySinkReady();
        }

        private void blockSink()
        {
            m_issinkready   = false;
            m_sinkstatus    = UnbufferedDataSinkStatus.BLOCKED;
        }

        private void notifySinkReady()
        {
            if( !m_issinkready )
            {
                m_issinkready = true;
                sinkReady();
            }
        }

        @Notifying
        public void needIncomingHandshakeMessage()
        {
            onNeedIncomingHandshakeMessage();

            if( !m_isactivated )
                return;

            enableSink();
        }

        @Notifying
        public void handshakeFinished()
        {
            onHandshakeFinished();

            if( !m_isactivated )
                return;

            enableSink();
        }


        @Override
        public int getMinimumSinkBufferSize()
        {
            return m_mininbufsize;
        }

        // This is intended for the master, that is, it is assumed: master!=null
        @Override
        public UnbufferedDataSinkStatus canProcessData()
        {
            return m_sinkstatus;
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
            // unwrap must not be called when the handshake status is NEED_WRAP.
            if( m_sinkstatus==UnbufferedDataSinkStatus.BLOCKED )
                return;

            assert m_isactivated;

            SSLEngineResult res = m_sslengine.unwrap( src, m_buffer );
            HandshakeStatus hs  = processEngineResult( res );

            switch( hs )
            {
            case NOT_HANDSHAKING:
                if( res.getStatus()==Status.CLOSED )
                    throw new NotImplementedException( res.getStatus().toString() );

                if( !m_buffer.hasRemaining() )
                    blockSink();
                else
                {
                    switch( res.getStatus() )
                    {
                    case OK:
                        m_sinkstatus = UnbufferedDataSinkStatus.CAN_PROCESS;
                        break;
                    case BUFFER_UNDERFLOW:
                        m_sinkstatus = UnbufferedDataSinkStatus.WAIT_FOR_DATA;
                        break;
                    default:
                        throw new IllegalStateException( res.getStatus().toString() );
                    }
                    m_issinkready = true;
                }

                if( res.bytesProduced()>0 )
                    notifySourceReady();

                break;
            case FINISHED:
                assert res.getStatus()==Status.OK;

                handshakeFinished();
                m_outbound.handshakeFinished();

                break;
            case NEED_UNWRAP:
                switch( res.getStatus() )
                {
                case OK:
                    m_sinkstatus = UnbufferedDataSinkStatus.CAN_PROCESS;
                    break;
                case BUFFER_UNDERFLOW:
                    m_sinkstatus = UnbufferedDataSinkStatus.WAIT_FOR_DATA;
                    break;
                default:
                    throw new NotImplementedException( res.getStatus().toString() );
                }
                m_issinkready = true;
                onNeedIncomingHandshakeMessage();
                break;
            case NEED_WRAP:
                disableSink();
                m_outbound.needOutgoingHandshakeMessage();
                break;
            default:
                throw new IllegalStateException( hs.toString() );
            }
        }


        private void onNeedIncomingHandshakeMessage()
        {
            if( s_logger.isDebugEnabled() )
                s_logger.debug( "{} SSL handshake needs incoming message", toString() );
        }


        private void onHandshakeFinished()
        {
            if( s_logger.isDebugEnabled() )
                s_logger.debug( "{} SSL handshake finished", toString() );
        }

        // The source part:

        private void notifySourceReady()
        {
            if( !m_issourceready )
            {
                m_issourceready = true;
                sourceReady();
            }
        }

        @Override
        public boolean hasData()
        {
            return CompactingSinkBuffers.hasData( m_buffer );
        }

        @Override
        protected boolean hasUnprocessedData()
        {
            return m_issourceready;
        }

        @Override
        public ByteBuffer startDataProcessing()
        {
            return CompactingSinkBuffers.startDataProcessing( m_buffer );
        }

        @Override
        public void finishDataProcessing()
        {
            CompactingSinkBuffers.finishDataProcessing( m_buffer );

            m_issourceready = false;

            // We are optimistic that m_buffer.hasRemaining() and don't check it.
            if( m_issinkenabled )
                unblockSink();
        }
    }


    public class SslOutbound extends AbstractAdaptiveToUnbufferedDataStage implements ExternalizableDataElement
    {
        private ByteBuffer      m_buffer;
        private ByteBuffer      m_srcbuf;
        private int             m_minoutbufsize   = 0;

        private boolean         m_isactivated     = false; // activated -> isOpen() && sinkMaster!=null && sourceMaster!=null
        private boolean         m_issourceenabled = false; // sourceenabled -> activated
        private boolean         m_issourceready   = false;
        private boolean         m_issinkready     = false;

        protected void adjustBuffer(int minappbufsize, int minoutbufsize)
        {
            adjustSinkBuffer( minappbufsize );
            m_minoutbufsize = minoutbufsize;
        }

        @Override
        protected void adjustSinkBuffer(int minbufsize)
        {
            m_buffer = Ssl.this.adjustBuffer( m_buffer, minbufsize );
            resetBuffer();
        }

        @Override
        protected ChronosDomainContext domainContext()
        {
            return m_domcntxt;
        }

        @Override
        public String toString()
        {
            return channelName() + "[SSL(O)]";
        }

        @Override
        public int getStateSize()
        {
            return CompactingSourceBuffers.getStateSize( m_srcbuf );
        }

        @Override
        public void saveState(ByteBuffer dst)
        {
            CompactingSourceBuffers.saveState( m_srcbuf, dst );
        }

        @Override
        public void installState(ByteBuffer src)
        {
            Preconditions.checkState( canPrepare() );
            Preconditions.checkState( !isActivated() );

            CompactingSourceBuffers.installState( m_buffer, src );

            m_srcbuf = m_buffer;
        }

        public boolean isBoundToMaster()
        {
            return sinkMaster()!=null && sourceMaster()!=null;
        }

        @Override
        @Notifying
        public void activate()
        {
            if( m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            Preconditions.checkState( sinkMaster()!=null && sourceMaster()!=null );

            m_isactivated = true;

            switch( m_sslengine.getHandshakeStatus() )
            {
            case NOT_HANDSHAKING:
                // We are optimistic that m_buffer.hasRemaining()
                enableSource();
                break;
            case NEED_UNWRAP:
                break;
            case NEED_WRAP:
                needOutgoingHandshakeMessage();
                break;
           default:
                throw new NotImplementedException( m_sslengine.getHandshakeStatus().toString() );
            }
        }

        @Override
        public void deactivate()
        {
            if( !m_isactivated )
                return;

            Preconditions.checkState( isInitialized() );
            assert sinkMaster()!=null && sourceMaster()!=null;

            disableSource();
            m_issinkready = false;
            m_isactivated = false;
        }

        @Override
        public boolean isActivated()
        {
            return m_isactivated;
        }

        public void clear()
        {
            resetBuffer();

            disableSource();
            m_issinkready = false;
            m_isactivated = false;
        }

        private void resetBuffer()
        {
            m_srcbuf = m_buffer;
            CompactingSourceBuffers.clear( m_srcbuf );
        }

        // The sink part:

        @Override
        public boolean canPrepare()
        {
            return m_srcbuf==m_buffer;
        }

        @Override
        protected int getAvailableBufferSize()
        {
            return CompactingSourceBuffers.getAvailableBufferSize( m_buffer );
        }

        @Override
        public ByteBuffer startPreparation()
        {
            return CompactingSourceBuffers.startPreparation( m_buffer );
        }

        @Override
        public void finishPreparation()
        {
            finishPreparation( m_buffer );
        }

        @Override
        protected void finishPreparation(ByteBuffer src)
        {
            CompactingSourceBuffers.finishPreparation( m_buffer );

            m_srcbuf = src;
            m_issinkready = false;

            // We are optimistic that m_buffer.hasRemaining() and don't check it.
            if( m_issourceenabled )
                notifySourceReady();
        }

        @Override
        protected UnbufferedDataSinkStatus canProcessData()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void processData(ByteBuffer src) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        // The source part:

        @Notifying
        private void enableSource()
        {
            assert m_isactivated;

            m_issourceenabled = true;

            notifySourceReady();
        }

        private void disableSource()
        {
            m_issourceready   = false;
            m_issourceenabled = false;
        }

        @Notifying
        private void notifySourceReady()
        {
            if( !m_issourceready )
            {
                m_issourceready = true;
                sourceReady();
            }
        }

        @Notifying
        public void needOutgoingHandshakeMessage()
        {
            if( s_logger.isDebugEnabled() )
                s_logger.debug( "{} SSL handshake needs outgoing message", toString() );

            if( !m_isactivated )
                return;

            enableSource();
        }

        @Notifying
        public void handshakeFinished()
        {
            if( s_logger.isDebugEnabled() )
                s_logger.debug( "{} SSL handshake finished", toString() );

            if( !m_isactivated )
                return;

            enableSource();
        }

        @Override
        public boolean hasData()
        {
            return m_issourceready;
        }

        @Override
        protected int getMinimumSinkBufferSize()
        {
            return 0;
        }

        @Override
        public int getMinimumSourceBufferSize()
        {
            return m_minoutbufsize;
        }

        @Override
        protected int getRequiredBufferSize()
        {
            return m_issourceready ? m_minoutbufsize : UnbufferedDataSource.NO_PENDING_DATA;
        }

        @Override
        protected boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return m_issourceready && bufsize>=m_minoutbufsize;
        }

        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
            // wrap must not be called when the handshake status is NEED_UNWRAP.
            if( !m_issourceready )
                return;
            else if( dst.remaining()<m_minoutbufsize )
            {
                m_issourceready = false;
                return;
            }

            SSLEngineResult res = m_sslengine.wrap( m_srcbuf, dst );

            if( res.getStatus()!=SSLEngineResult.Status.OK && res.getStatus()!=Status.CLOSED )
                throw new NotImplementedException( res.getStatus().toString() );

            HandshakeStatus hs = processEngineResult( res );

            switch( hs )
            {
            case NOT_HANDSHAKING:
                // canPrepare() checks the source buffer, hence we have to restore it first if a caller buffer was used.
                if( !m_srcbuf.hasRemaining() )
                {
                    m_issourceready = false;

                    if( m_srcbuf!=m_buffer )
                        m_srcbuf = m_buffer;
                }
                if( !m_issinkready && canPrepare() && res.bytesConsumed()>0 )
                {
                    m_issinkready = true;
                    sinkReady();
                }
                break;
            case FINISHED:
                handshakeFinished();
                m_inbound.handshakeFinished();
                break;
            case NEED_UNWRAP:
                disableSource();
                m_inbound.needIncomingHandshakeMessage();
                break;
            case NEED_WRAP:
                needOutgoingHandshakeMessage();
                break;
            default:
                throw new IllegalStateException( hs.toString() );
            }
        }
    }


    private ByteBuffer adjustBuffer(ByteBuffer buffer, int minbufsize)
    {
        return buffer==null || buffer.capacity()<minbufsize ? ByteBuffer.allocate( minbufsize ) : buffer;

    }


    private HandshakeStatus processEngineResult(SSLEngineResult res)
    {
        HandshakeStatus hs = res.getHandshakeStatus();

        while( hs==HandshakeStatus.NEED_TASK )
        {
            // This could block ...
            Runnable task;
            while( ( task = m_sslengine.getDelegatedTask() )!=null )
                task.run();
            hs = m_sslengine.getHandshakeStatus();
        }

        return hs;
    }

}
