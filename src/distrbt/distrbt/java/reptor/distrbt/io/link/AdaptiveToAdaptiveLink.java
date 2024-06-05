package reptor.distrbt.io.link;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.io.AdaptiveDataSink;
import reptor.distrbt.io.AdaptiveDataSource;
import reptor.distrbt.io.GenericDataLinkElement;


public class AdaptiveToAdaptiveLink extends AbstractDataLink
{

    private final AdaptiveDataSource  m_source;
    private final AdaptiveDataSink    m_sink;


    public AdaptiveToAdaptiveLink(AdaptiveDataSource source, AdaptiveDataSink sink)
    {
        m_source = Objects.requireNonNull( source );
        m_sink   = Objects.requireNonNull( sink );
    }


    @Override
    protected GenericDataLinkElement source()
    {
        return m_source;
    }


    @Override
    protected GenericDataLinkElement sink()
    {
        return m_sink;
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        if( elem==m_sink || isSourceReady() )
            notifyReady();
    }


    private boolean isSourceReady()
    {
        return m_sink.canPrepare() && m_source.canProcessData( m_sink.hasRemaining(), m_sink.getAvailableBufferSize() );
    }


    private boolean isSinkReady()
    {
        return m_sink.isReady();
    }


    @Override
    public void execute() throws IOException
    {
        if( isSourceReady() )
        {
            ByteBuffer buffer = m_sink.startPreparation();

            buffer = m_source.startDataProcessing( buffer );

            m_sink.finishPreparation( buffer );
        }

        if( isSinkReady() )
        {
            m_sink.execute();

            if( !m_sink.hasRemaining() )
                m_source.finishDataProcessing();
        }

        isDone( !isSourceReady() && !isSinkReady() );
    }

}