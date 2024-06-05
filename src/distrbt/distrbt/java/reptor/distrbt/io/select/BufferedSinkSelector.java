package reptor.distrbt.io.select;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.common.base.Preconditions;

import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.io.BufferedDataSink;


public class BufferedSinkSelector extends AbstractDataEndpointSelector<BufferedDataSink, BufferedDataSink, SynchronousSource>
                                  implements BufferedDataSink
{

    @Override
    public BufferedDataSink getSink()
    {
        return this;
    }


    @Override
    public SynchronousSource getSource()
    {
        return null;
    }


    @Override
    public boolean hasRemaining()
    {
        return m_isactivated && m_selected.hasRemaining();
    }


    @Override
    public boolean canPrepare()
    {
        return m_isactivated && m_selected.canPrepare();
    }


    @Override
    public int getAvailableBufferSize()
    {
        return m_isactivated ? m_selected.getAvailableBufferSize() : 0;
    }


    @Override
    public ByteBuffer startPreparation()
    {
        return m_isactivated ? m_selected.startPreparation() : null;
    }


    @Override
    public void finishPreparation()
    {
        if( m_isactivated )
            m_selected.finishPreparation();
    }


    @Override
    public boolean isReady()
    {
        return m_isactivated && m_selected.isReady();
    }


    @Override
    public void execute() throws IOException
    {
        if( !m_isactivated )
            return;

        m_selected.execute();
    }


    @Override
    public void adjustBuffer(int minbufsize)
    {
        Preconditions.checkState( m_selected!=null );

        m_selected.adjustBuffer( minbufsize );
    }

}
