package reptor.distrbt.io.net;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.net.Network.NetworkSink;
import reptor.jlib.NotImplementedException;


public class BufferedToNetworkLink extends AbstractDataLinkElement
                                   implements DataLink, DataChannelContext<SelectorDomainContext>,
                                              NetworkEndpointContext<SelectorDomainContext>
{

    private DataChannelContext<? extends SelectorDomainContext>  m_master;

    private final BufferedDataSource    m_source;
    private final NetworkSink           m_sink;
    private boolean                     m_sinkenabled;


    public BufferedToNetworkLink(BufferedDataSource source, NetworkSink sink)
    {
        m_source = Objects.requireNonNull( source );
        m_sink   = Objects.requireNonNull( sink );
    }


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );

        m_master = master;
        m_source.bindToMaster( this );
        m_sink.bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        throw new NotImplementedException();
    }


    @Override
    protected DataChannelContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    @Override
    public void endpointActivated(NetworkEndpoint elem)
    {
        disableSink();
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        if( elem==m_source || m_source.hasData() )
            notifyReady();
    }


    private boolean isSourceReady()
    {
        return m_source.isReady();
    }


    private boolean isSinkReady()
    {
        return m_source.hasData() && ( !m_sinkenabled || m_sink.canProcessData() );
    }


    @Override
    public void execute() throws IOException
    {
        if( isSourceReady() )
            m_source.execute();

        if( isSinkReady() )
        {
            ByteBuffer buffer = m_source.startDataProcessing();

            m_sink.processData( buffer );

            if( !m_sinkenabled && buffer.hasRemaining() )
                enableSink();
            else if( m_sinkenabled && !buffer.hasRemaining() )
                disableSink();

            m_source.finishDataProcessing();
        }

        if( !isSourceReady() && !isSinkReady() )
            clearReady();
    }


    private void enableSink()
    {
        m_sink.enable();
        m_sinkenabled = true;
    }


    private void disableSink()
    {
        m_sink.disable();
        m_sinkenabled = false;
    }

}