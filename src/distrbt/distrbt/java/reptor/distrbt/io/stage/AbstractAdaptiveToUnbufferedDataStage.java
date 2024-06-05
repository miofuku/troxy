package reptor.distrbt.io.stage;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.io.AdaptiveDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;


public abstract class AbstractAdaptiveToUnbufferedDataStage
        extends AbstractDataStage<AdaptiveDataSink, UnbufferedDataSource>
{

    private final Sink      m_sink   = new Sink();
    private final Source    m_source = new Source();


    protected void sinkReady()
    {
        sinkMaster().dataReady( m_sink );
    }


    @Override
    public AdaptiveDataSink getSink()
    {
        return m_sink;
    }


    protected abstract int getMinimumSinkBufferSize();


    protected abstract void adjustSinkBuffer(int minbufsize);


    protected abstract boolean canPrepare();


    protected abstract int getAvailableBufferSize();


    protected abstract ByteBuffer startPreparation();


    protected abstract void finishPreparation();


    protected abstract void finishPreparation(ByteBuffer src);


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


    protected abstract boolean hasData();


    protected abstract int getRequiredBufferSize();


    protected abstract boolean canRetrieveData(boolean hasremaining, int bufsize);


    protected abstract void retrieveData(ByteBuffer dst) throws IOException;


    protected abstract UnbufferedDataSinkStatus canProcessData();


    protected abstract void processData(ByteBuffer src) throws IOException;


    private class Sink extends AbstractSink implements AdaptiveDataSink
    {
        @Override
        public int getMinimumBufferSize()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.getMinimumSinkBufferSize();
        }

        @Override
        public void adjustBuffer(int minbufsize)
        {
            AbstractAdaptiveToUnbufferedDataStage.this.adjustSinkBuffer( minbufsize );
        }

        @Override
        public boolean canPrepare()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.canPrepare();
        }

        @Override
        public int getAvailableBufferSize()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.getAvailableBufferSize();
        }

        @Override
        public ByteBuffer startPreparation()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.startPreparation();
        }

        @Override
        public void finishPreparation()
        {
            AbstractAdaptiveToUnbufferedDataStage.this.finishPreparation();
        }

        @Override
        public void finishPreparation(ByteBuffer src)
        {
            AbstractAdaptiveToUnbufferedDataStage.this.finishPreparation( src );
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
        public UnbufferedDataSinkStatus canProcessData()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.canProcessData();
        }

        @Override
        public void processData(ByteBuffer src) throws IOException
        {
            AbstractAdaptiveToUnbufferedDataStage.this.processData( src );
        }

        @Override
        public boolean hasRemaining()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.hasData();
        }
    }


    private class Source extends AbstractSource implements UnbufferedDataSource
    {
        @Override
        public int getMinimumBufferSize()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.getMinimumSourceBufferSize();
        }

        @Override
        public boolean isReady()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.hasData();
        }

        @Override
        public int getRequiredBufferSize()
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.getRequiredBufferSize();
        }

        @Override
        public boolean canRetrieveData(boolean hasremaining, int bufsize)
        {
            return AbstractAdaptiveToUnbufferedDataStage.this.canRetrieveData( hasremaining, bufsize);
        }


        @Override
        public void retrieveData(ByteBuffer dst) throws IOException
        {
            AbstractAdaptiveToUnbufferedDataStage.this.retrieveData( dst );
        }
    }

}
