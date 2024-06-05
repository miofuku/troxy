package reptor.distrbt.io.channel;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.GenericDataLinkElement;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.link.UnbufferedToBufferedLink;


public class BinaryBufferedSinkChannel extends BinaryDataChannel<BufferedDataSink, SynchronousSource>
                                       implements BufferedDataSink
{

    private final BufferedDataSink  m_insink;
    private final DataLink          m_outlink;


    public BinaryBufferedSinkChannel(CommunicationStage<? extends BufferedDataSink, ? extends UnbufferedDataSource> stage,
                                     BufferedDataSink outsink)
    {
        m_insink    = stage.getSink();
        m_outlink   = new UnbufferedToBufferedLink( stage.getSource(), outsink );
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
    public void adjustBuffer(int minbufsize)
    {
        m_insink.adjustBuffer( minbufsize );
    }


    @Override
    public boolean hasRemaining()
    {
        return m_insink.hasRemaining();
    }


    @Override
    public boolean canPrepare()
    {
        return m_insink.canPrepare();
    }


    @Override
    public int getAvailableBufferSize()
    {
        return m_insink.getAvailableBufferSize();
    }


    @Override
    public ByteBuffer startPreparation()
    {
        return m_insink.startPreparation();
    }


    @Override
    public void finishPreparation()
    {
        m_insink.finishPreparation();
    }


    @Override
    public void execute() throws IOException
    {
        if( m_insink.isReady() )
            m_insink.execute();

        if( m_outlink.isReady() )
            m_outlink.execute();

        isDone( !m_insink.isReady() && !m_outlink.isReady() );
    }

}
