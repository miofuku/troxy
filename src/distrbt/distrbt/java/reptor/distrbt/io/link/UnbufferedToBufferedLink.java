package reptor.distrbt.io.link;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSource;


public class UnbufferedToBufferedLink extends AbstractDataLink
{

    private final UnbufferedDataSource m_source;
    private final BufferedDataSink     m_sink;


    public UnbufferedToBufferedLink(UnbufferedDataSource source, BufferedDataSink sink)
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
        return m_sink.canPrepare() && m_source.canRetrieveData( m_sink.hasRemaining(), m_sink.getAvailableBufferSize() );
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

            m_source.retrieveData( buffer );

            m_sink.finishPreparation();
        }

        if( isSinkReady() )
            m_sink.execute();

        isDone( !isSourceReady() && !isSinkReady() );
    }

}