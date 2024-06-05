package reptor.distrbt.io.select;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.SynchronousSink;
import reptor.distrbt.io.UnbufferedDataSource;


public class UnbufferedSourceSelector extends AbstractDataEndpointSelector<UnbufferedDataSource, SynchronousSink, UnbufferedDataSource>
        implements UnbufferedDataSource
{

    @Override
    public SynchronousSink getSink()
    {
        return null;
    }


    @Override
    public UnbufferedDataSource getSource()
    {
        return this;
    }


    @Override
    public int getMinimumBufferSize()
    {
        return m_selected==null ? 0 : m_selected.getMinimumBufferSize();
    }


    @Override
    public boolean isReady()
    {
        return m_isactivated && m_selected.isReady();
    }


    @Override
    public int getRequiredBufferSize()
    {
        return m_isactivated ? m_selected.getRequiredBufferSize() : NO_PENDING_DATA;
    }


    @Override
    public boolean canRetrieveData(boolean hasremaining, int bufsize)
    {
        return m_isactivated && m_selected.canRetrieveData( hasremaining, bufsize );
    }


    @Override
    public void retrieveData(ByteBuffer dst) throws IOException
    {
        if( m_isactivated )
            m_selected.retrieveData( dst );
    }

}
