package reptor.distrbt.io.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.link.BufferedToUnbufferedLink;


public class BinaryBufferedSourceChannel extends BinaryDataChannel<SynchronousSink, BufferedDataSource>
                                         implements BufferedDataSource
{

    private final DataLink              m_inlink;
    private final BufferedDataSource    m_outsource;


    public BinaryBufferedSourceChannel(BufferedDataSource insource,
                                       CommunicationStage<? extends UnbufferedDataSink, ? extends BufferedDataSource> stage)
    {
        m_inlink    = new BufferedToUnbufferedLink( insource, stage.getSink() );
        m_outsource = Objects.requireNonNull( stage.getSource() );
    }


    @Override
    protected GenericDataLinkElement in()
    {
        return m_inlink;
    }


    @Override
    protected GenericDataLinkElement out()
    {
        return m_outsource;
    }


    @Override
    public void adjustBuffer(int minbufsize)
    {
        m_outsource.adjustBuffer( minbufsize );
    }


    @Override
    public void execute() throws IOException
    {
        if( m_inlink.isReady() )
            m_inlink.execute();

        if( m_outsource.isReady() )
            m_outsource.execute();

        isDone( !m_inlink.isReady() && !m_outsource.isReady() );
    }


    @Override
    public boolean hasData()
    {
        return m_outsource.hasData();
    }


    @Override
    public boolean hasUnprocessedData()
    {
        return m_outsource.hasUnprocessedData();
    }


    @Override
    public ByteBuffer startDataProcessing()
    {
        return m_outsource.startDataProcessing();
    }


    @Override
    public void finishDataProcessing()
    {
        m_outsource.finishDataProcessing();
    }

}
