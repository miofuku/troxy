package reptor.distrbt.io;

import java.nio.ByteBuffer;

import reptor.chronos.com.SynchronousSink;

// Notifies the master if it can prepare work or if it is ready to execute.
public interface BufferedDataSink extends SynchronousSink, BufferedEndpoint, DataChannelTask
{
    boolean     hasRemaining();
    boolean     canPrepare();
    int         getAvailableBufferSize();
    ByteBuffer  startPreparation();
    void        finishPreparation();

    default void adjustBuffer(UnbufferedDataSource source)
    {
        adjustBuffer( source.getMinimumBufferSize() );
    }
}