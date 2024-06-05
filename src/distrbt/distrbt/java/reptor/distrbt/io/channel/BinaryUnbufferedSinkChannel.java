package reptor.distrbt.io.channel;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.link.BufferedToUnbufferedLink;


public class BinaryUnbufferedSinkChannel extends BinaryDataChannel<UnbufferedDataSink, SynchronousSource>
                                         implements UnbufferedDataSink
{

    private final UnbufferedDataSink    m_insink;
    private final DataLink              m_outlink;


    public BinaryUnbufferedSinkChannel(CommunicationStage<? extends UnbufferedDataSink, ? extends BufferedDataSource> stage,
                                       UnbufferedDataSink outsink)
    {
        m_insink    = stage.getSink();
        m_outlink   = new BufferedToUnbufferedLink( stage.getSource(), outsink );
    }


    @Override
    protected GenericDataLinkElement in()
    {
        return m_insink;
    }


    @Override
    protected GenericDataLinkElement out()
    {
        return m_outlink;
    }


    @Override
    public int getMinimumBufferSize()
    {
        return m_insink.getMinimumBufferSize();
    }


    @Override
    public UnbufferedDataSinkStatus canProcessData()
    {
        return isReady() ? UnbufferedDataSinkStatus.CAN_PROCESS : m_insink.canProcessData();
    }


    @Override
    public void processData(ByteBuffer src) throws IOException
    {
        m_insink.processData( src );

        if( m_outlink.isReady() )
            m_outlink.execute();

        isDone( !m_outlink.isReady() );
    }

}
