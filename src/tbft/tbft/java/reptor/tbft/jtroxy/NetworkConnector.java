package reptor.tbft.jtroxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.ConnectorEndpoint;
import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.tbft.TroxyNetworkResults;


abstract class NetworkConnector
{

    protected NetworkInbound            m_inbound;
    protected NetworkOutbound           m_outbound;
    private   TroxyNetworkResults       m_results;


    protected void initEndpoint(SelectorDomainContext domcntxt, ConnectorEndpoint<? extends UnbufferedDataSink, ? extends UnbufferedDataSource> conn)
    {
        m_inbound  = new NetworkInbound( domcntxt, conn.getInboundConnect() );
        m_outbound = new NetworkOutbound( domcntxt, conn.getOutboundConnect() );
    }


    protected void initNetworkResults(TroxyNetworkResults results)
    {
        m_results = results;

        m_inbound.updateStatus();
        m_outbound.updateStatus();
    }


    protected abstract class NetworkStage implements DataChannelContext<SelectorDomainContext>
    {
        private final SelectorDomainContext m_domcntxt;

        public NetworkStage(SelectorDomainContext domcntxt)
        {
            m_domcntxt = domcntxt;
        }

        @Override
        public SelectorDomainContext getDomainContext()
        {
            return m_domcntxt;
        }

        @Override
        public String getChannelName()
        {
            return NetworkConnector.this.toString();
        }
    }


    protected class NetworkInbound extends NetworkStage
    {
        private final UnbufferedDataSink   m_sink;

        public NetworkInbound(SelectorDomainContext domcntxt, UnbufferedDataSink sink)
        {
            super( domcntxt );

            m_sink = sink;
            m_sink.bindToMaster( this );
        }

        private void updateStatus()
        {
            m_results.canProcessInboundData( m_sink.canProcessData() );
        }

        @Override
        public void dataReady(SynchronousLinkElement elem)
        {
            updateStatus();
        }

        public void processData(ByteBuffer src) throws IOException
        {
            m_sink.processData( src );
            updateStatus();
        }
    }


    protected class NetworkOutbound extends NetworkStage
    {
        private final UnbufferedDataSource m_source;

        public NetworkOutbound(SelectorDomainContext domcntxt, UnbufferedDataSource source)
        {
            super( domcntxt );

            m_source = source;
            m_source.bindToMaster( this );
        }

        private void updateStatus()
        {
            m_results.setRequiredOutboundBufferSize( m_source.getRequiredBufferSize() );
        }

        @Override
        public void dataReady(SynchronousLinkElement elem)
        {
            updateStatus();
        }

        public void retrieveData(ByteBuffer dst) throws IOException
        {
            m_source.retrieveData( dst );
            updateStatus();
        }
    }

}
