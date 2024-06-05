package reptor.distrbt.io;

import java.nio.ByteBuffer;

import reptor.chronos.com.SynchronousSource;

// Notifies the master if it is ready to execute or if it has new data to be processed.
public interface BufferedDataSource extends SynchronousSource, BufferedEndpoint, DataChannelTask
{
    // Is reset when finishDataProcessing() is called.
    boolean     hasUnprocessedData();
    // Returns true if there is processed or unprocessed data. (Must not be true when the source is disabled.)
    boolean     hasData();
    ByteBuffer  startDataProcessing();
    void        finishDataProcessing();

    default void adjustBuffer(UnbufferedDataSink sink)
    {
        adjustBuffer( sink.getMinimumBufferSize() );
    }
}