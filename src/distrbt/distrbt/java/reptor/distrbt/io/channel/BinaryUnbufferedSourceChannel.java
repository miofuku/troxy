package reptor.distrbt.io.channel;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousSink;
import reptor.distrbt.io.AdaptiveDataSink;
import reptor.distrbt.io.AdaptiveDataSource;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.link.AdaptiveToAdaptiveLink;
import reptor.distrbt.io.link.UnbufferedToBufferedLink;


public class BinaryUnbufferedSourceChannel extends BinaryDataChannel<SynchronousSink, UnbufferedDataSource>
                                           implements UnbufferedDataSource
{

    private final DataLink              m_inlink;
    private final UnbufferedDataSource  m_outsource;


    public BinaryUnbufferedSourceChannel(UnbufferedDataSource insource,
                                         CommunicationStage<? extends BufferedDataSink, ? extends UnbufferedDataSource> stage)
    {
        this( new UnbufferedToBufferedLink( insource, stage.getSink() ), stage );
    }


    public BinaryUnbufferedSourceChannel(AdaptiveDataSource insource,
                                         CommunicationStage<AdaptiveDataSink, ? extends UnbufferedDataSource> stage)
    {
        this( new AdaptiveToAdaptiveLink( insource, stage.getSink() ), stage );
    }


    private BinaryUnbufferedSourceChannel(DataLink inlink,
                                          CommunicationStage<? extends BufferedDataSink, ? extends UnbufferedDataSource> stage)
    {
        m_inlink    = inlink;
        m_outsource = stage.getSource();
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
    public int getMinimumBufferSize()
    {
        return m_outsource.getMinimumBufferSize();
    }


    @Override
    public int getRequiredBufferSize()
    {
        return isReady() ? 0 : m_outsource.getRequiredBufferSize();
    }


    @Override
    public boolean canRetrieveData(boolean hasremaining, int bufsize)
    {
        return isReady() || m_outsource.canRetrieveData( hasremaining, bufsize );
    }


    @Override
    public void retrieveData(ByteBuffer dst) throws IOException
    {
        if( m_inlink.isReady() )
            m_inlink.execute();

        if( m_outsource.canRetrieveData( dst.position()>0, dst.remaining() ) )
            m_outsource.retrieveData( dst );

        isDone( !m_inlink.isReady() );
    }

}
