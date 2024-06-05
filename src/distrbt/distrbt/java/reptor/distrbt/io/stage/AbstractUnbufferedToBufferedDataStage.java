package reptor.distrbt.io.stage;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;


public abstract class AbstractUnbufferedToBufferedDataStage
        extends AbstractDataStage<UnbufferedDataSink, BufferedDataSource>
{

    private final Sink      m_sink   = new Sink();
    private final Source    m_source = new Source();


    protected void sinkReady()
    {
        sinkMaster().dataReady( m_sink );
    }


    @Override
    public UnbufferedDataSink getSink()
    {
        return m_sink;
    }


    protected abstract int getMinimumSinkBufferSize();


    protected abstract UnbufferedDataSinkStatus canProcessData();


    protected abstract void processData(ByteBuffer src) throws IOException;


    protected void sourceReady()
    {
        sourceMaster().dataReady( m_source );
    }


    @Override
    public BufferedDataSource getSource()
    {
        return m_source;
    }


    protected abstract void adjustSourceBuffer(int minbufsize);


    protected abstract boolean hasData();


    protected abstract boolean hasUnprocessedData();


    protected abstract ByteBuffer startDataProcessing();


    protected abstract void finishDataProcessing();


    private class Sink extends AbstractSink implements UnbufferedDataSink
    {
        @Override
        public int getMinimumBufferSize()
        {
            return AbstractUnbufferedToBufferedDataStage.this.getMinimumSinkBufferSize();
        }

        @Override
        public boolean isReady()
        {
            return AbstractUnbufferedToBufferedDataStage.this.canProcessData()==UnbufferedDataSinkStatus.CAN_PROCESS;
        }

        @Override
        public UnbufferedDataSinkStatus canProcessData()
        {
            return AbstractUnbufferedToBufferedDataStage.this.canProcessData();
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
            AbstractUnbufferedToBufferedDataStage.this.processData( src );
        }
    }


    private class Source extends AbstractSource implements BufferedDataSource
    {
        @Override
        public void adjustBuffer(int minbufsize)
        {
            AbstractUnbufferedToBufferedDataStage.this.adjustSourceBuffer( minbufsize );
        }

        @Override
        public boolean isReady()
        {
            return false;
        }

        @Override
        public void execute() throws IOException
        {
        }

        @Override
        public boolean hasData()
        {
            return AbstractUnbufferedToBufferedDataStage.this.hasData();
        }

        @Override
        public boolean hasUnprocessedData()
        {
            return AbstractUnbufferedToBufferedDataStage.this.hasUnprocessedData();
        }

        @Override
        public ByteBuffer startDataProcessing()
        {
            return AbstractUnbufferedToBufferedDataStage.this.startDataProcessing();
        }

        @Override
        public void finishDataProcessing()
        {
            AbstractUnbufferedToBufferedDataStage.this.finishDataProcessing();
        }
    }

}
