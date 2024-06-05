package reptor.tbft.adapt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.chronos.com.SynchronousEndpoint;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyNetworkResults;


public abstract class TroxyNetworkConnector implements ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>
{

    protected final Troxy           m_troxy;

    private final TroxyInbound      m_inbound  = new TroxyInbound();
    private final TroxyOutbound     m_outbound = new TroxyOutbound();


    public TroxyNetworkConnector(Troxy troxy)
    {
        m_troxy = Objects.requireNonNull( troxy );
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


    private static abstract class TroxyEndpoint extends AbstractDataLinkElement
                                                implements SynchronousEndpoint, GenericDataLinkElement
    {
        private DataChannelContext<? extends SelectorDomainContext>  m_master;


        @Override
        public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            Preconditions.checkState( m_master==null );

            m_master = master;
        }


        @Override
        public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );

            m_master = null;
        }

        @Override
        protected DataChannelContext<? extends SelectorDomainContext> master()
        {
            return m_master;
        }
    }


    private class TroxyInbound extends TroxyEndpoint implements UnbufferedDataSink
    {
        private UnbufferedDataSinkStatus    m_status = UnbufferedDataSinkStatus.BLOCKED;

        @Override
        public int getMinimumBufferSize()
        {
            return TroxyNetworkConnector.this.getMinimumSinkBufferSize();
        }

        @Notifying
        public void processResult(TroxyNetworkResults result)
        {
            UnbufferedDataSinkStatus status = result.canProcessInboundData();

            if( status!=m_status )
            {
                UnbufferedDataSinkStatus prevstatus = m_status;

                m_status = status;

                if( prevstatus==UnbufferedDataSinkStatus.BLOCKED || status==UnbufferedDataSinkStatus.CAN_PROCESS )
                    notifyReady();
                else
                    clearReady();
            }
        }

        @Override
        public UnbufferedDataSinkStatus canProcessData()
        {
            return m_status;
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
            assert m_status!=UnbufferedDataSinkStatus.BLOCKED;

            TroxyNetworkResults result = processInboundData( src );

            processResult( result );
            m_outbound.processResult( result );
        }
    }


    private class TroxyOutbound extends TroxyEndpoint implements UnbufferedDataSource
    {
        private int     m_reqbufsize = NO_PENDING_DATA;

        @Override
        public int getMinimumBufferSize()
        {
            return TroxyNetworkConnector.this.getMinimumSourceBufferSize();
        }

        @Notifying
        public void processResult(TroxyNetworkResults result)
        {
            int reqbufsize = result.getRequiredOutboundBufferSize();

            if( reqbufsize!=m_reqbufsize )
            {
                m_reqbufsize = reqbufsize;

                if( reqbufsize!=NO_PENDING_DATA )
                    notifyReady();
                else
                    clearReady();
            }
        }

        @Override
        public int getRequiredBufferSize()
        {
            return m_reqbufsize;
        }

        @Override
        public boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return bufsize>=m_reqbufsize;
        }

        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
            assert dst.remaining()>=m_reqbufsize;

            TroxyNetworkResults result = retrieveOutboundData( dst );

            processResult( result );
            m_inbound.processResult( result );
        }
    }


    protected void processNetworkResults(TroxyNetworkResults result)
    {
        m_inbound.processResult( result );
        m_outbound.processResult( result );
    }


    protected abstract int getMinimumSinkBufferSize();

    protected abstract int getMinimumSourceBufferSize();

    protected abstract TroxyNetworkResults processInboundData(ByteBuffer src) throws IOException;

    protected abstract TroxyNetworkResults retrieveOutboundData(ByteBuffer dst) throws IOException;

}
