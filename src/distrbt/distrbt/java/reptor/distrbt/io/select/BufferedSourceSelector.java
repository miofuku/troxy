package reptor.distrbt.io.select;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import reptor.chronos.com.SynchronousSink;
import reptor.distrbt.io.BufferedDataSource;


public class BufferedSourceSelector extends AbstractDataEndpointSelector<BufferedDataSource, SynchronousSink, BufferedDataSource>
                                    implements BufferedDataSource
{

    private final Consumer<? super BufferedDataSource> m_chooser;

    private boolean m_isfixed = false;


    public BufferedSourceSelector(Consumer<? super BufferedDataSource> chooser)
    {
        m_chooser = Objects.requireNonNull( chooser );
    }


    @Override
    public SynchronousSink getSink()
    {
        return null;
    }


    @Override
    public BufferedDataSource getSource()
    {
        return this;
    }


    @Override
    public void clear()
    {
        m_isfixed = false;
        super.clear();
    }


    public void select(BufferedDataSource source, boolean isfixed)
    {
        select( source );

        m_isfixed = isfixed;
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

        if( !m_isfixed && m_selected.hasUnprocessedData() )
            chooseStage();
    }


    protected void chooseStage()
    {
        m_chooser.accept( m_selected );
    }


    @Override
    public boolean hasData()
    {
        return m_isactivated && m_selected.hasData();
    }


    @Override
    public boolean hasUnprocessedData()
    {
        return m_isactivated && m_selected.hasUnprocessedData();
    }


    @Override
    public ByteBuffer startDataProcessing()
    {
        return m_isactivated ? m_selected.startDataProcessing() : null;
    }


    @Override
    public void finishDataProcessing()
    {
        if( m_isactivated )
            m_selected.finishDataProcessing();
    }


    @Override
    public void adjustBuffer(int minbufsize)
    {
        Preconditions.checkState( m_selected!=null );

        m_selected.adjustBuffer( minbufsize );
    }

}
