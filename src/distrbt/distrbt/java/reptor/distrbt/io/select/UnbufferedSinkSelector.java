package reptor.distrbt.io.select;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;


public class UnbufferedSinkSelector extends AbstractDataEndpointSelector<UnbufferedDataSink, UnbufferedDataSink, SynchronousSource>
        implements UnbufferedDataSink
{

    private final Consumer<? super ByteBuffer> m_chooser;
    private final int                          m_minbufsize;

    private UnbufferedDataSinkStatus m_status = UnbufferedDataSinkStatus.CAN_PROCESS;


    public UnbufferedSinkSelector(Consumer<? super ByteBuffer> chooser, int minbufsize)
    {
        m_chooser    = Objects.requireNonNull( chooser );
        m_minbufsize = minbufsize;
    }


    @Override
    public UnbufferedDataSink getSink()
    {
        return this;
    }


    @Override
    public SynchronousSource getSource()
    {
        return null;
    }


    @Override
    public int getMinimumBufferSize()
    {
        return m_selected==null ? m_minbufsize : m_selected.getMinimumBufferSize();
    }


    public void signal()
    {
        m_status = UnbufferedDataSinkStatus.CAN_PROCESS;
        m_master.dataReady( this );
    }


    @Override
    public boolean isReady()
    {
        return m_selected!=null && m_selected.isReady() || m_isactivated;
    }


    @Override
    public UnbufferedDataSinkStatus canProcessData()
    {
        if( m_selected!=null )
            return m_selected.canProcessData();
        else if( !m_isactivated )
            return UnbufferedDataSinkStatus.BLOCKED;
        else
            return m_status;
    }


    @Override
    public void processData(ByteBuffer src) throws IOException
    {
        if( m_selected!=null )
            m_selected.processData( src );
        else if( m_isactivated )
        {
            chooseStage( src );

            if( m_selected!=null )
                m_selected.processData( src );
            else
                m_status = UnbufferedDataSinkStatus.WAIT_FOR_DATA;
        }
    }


    protected void chooseStage(ByteBuffer src)
    {
        m_chooser.accept( src );
    }

}
