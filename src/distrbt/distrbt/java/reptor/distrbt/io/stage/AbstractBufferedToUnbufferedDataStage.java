package reptor.distrbt.io.stage;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;


public abstract class AbstractBufferedToUnbufferedDataStage
        extends AbstractDataStage<BufferedDataSink, UnbufferedDataSource>
{

    private final Sink      m_sink   = new Sink();
    private final Source    m_source = new Source();


    protected void sinkReady()
    {
        sinkMaster().dataReady( m_sink );
    }


    @Override
    public BufferedDataSink getSink()
    {
        return m_sink;
    }


    protected abstract void adjustSinkBuffer(int minbufsize);


    protected abstract boolean canPrepare();


    protected abstract int getAvailableBufferSize();


    protected abstract ByteBuffer startPreparation();


    protected abstract void finishPreparation();


    protected abstract boolean hasRemaining();


    protected void sourceReady()
    {
        sourceMaster().dataReady( m_source );
    }


    @Override
    public UnbufferedDataSource getSource()
    {
        return m_source;
    }


    protected abstract int getMinimumSourceBufferSize();


    protected abstract int getRequiredBufferSize();


    protected abstract boolean canRetrieveData(boolean hasremaining, int bufsize);


    protected abstract void retrieveData(ByteBuffer dst) throws IOException;


    private class Sink extends AbstractSink implements BufferedDataSink
    {
        @Override
        public void adjustBuffer(int minbufsize)
        {
            AbstractBufferedToUnbufferedDataStage.this.adjustSinkBuffer( minbufsize );
        }

        @Override
        public boolean canPrepare()
        {
            return AbstractBufferedToUnbufferedDataStage.this.canPrepare();
        }

        @Override
        public int getAvailableBufferSize()
        {
            return AbstractBufferedToUnbufferedDataStage.this.getAvailableBufferSize();
        }

        @Override
        public ByteBuffer startPreparation()
        {
            return AbstractBufferedToUnbufferedDataStage.this.startPreparation();
        }

        @Override
        public void finishPreparation()
        {
            AbstractBufferedToUnbufferedDataStage.this.finishPreparation();
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
        public boolean hasRemaining()
        {
            return AbstractBufferedToUnbufferedDataStage.this.hasRemaining();
        }
    }


    private class Source extends AbstractSource implements UnbufferedDataSource
    {
        @Override
        public int getMinimumBufferSize()
        {
            return AbstractBufferedToUnbufferedDataStage.this.getMinimumSourceBufferSize();
        }

        @Override
        public boolean isReady()
        {
            return AbstractBufferedToUnbufferedDataStage.this.hasRemaining();
        }

        @Override
        public int getRequiredBufferSize()
        {
            return AbstractBufferedToUnbufferedDataStage.this.getRequiredBufferSize();
        }

        @Override
        public boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return AbstractBufferedToUnbufferedDataStage.this.canRetrieveData( hasremaining, bufsize );
        }

        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
            AbstractBufferedToUnbufferedDataStage.this.retrieveData( dst );
        }
    }

}
