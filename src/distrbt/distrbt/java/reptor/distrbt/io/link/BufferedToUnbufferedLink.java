package reptor.distrbt.io.link;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;


public class BufferedToUnbufferedLink extends AbstractDataLink
{

    private final BufferedDataSource    m_source;
    private final UnbufferedDataSink    m_sink;


    public BufferedToUnbufferedLink(BufferedDataSource source, UnbufferedDataSink sink)
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
        if( !isReady() && ( elem==m_source || isSinkReady() ) )
            notifyReady();
    }


    private boolean isSourceReady()
    {
        return m_source.isReady();
    }


    private boolean isSinkReady()
    {
        return m_sink.canProcessData()==UnbufferedDataSinkStatus.CAN_PROCESS && m_source.hasData() ||
               m_sink.canProcessData()==UnbufferedDataSinkStatus.WAIT_FOR_DATA && m_source.hasUnprocessedData();
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

            m_source.finishDataProcessing();
        }

        isDone( !isSourceReady() && !isSinkReady() );
    }

}