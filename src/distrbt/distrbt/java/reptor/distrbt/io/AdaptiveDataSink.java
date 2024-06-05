package reptor.distrbt.io;

import java.nio.ByteBuffer;


public interface AdaptiveDataSink extends BufferedDataSink, UnbufferedDataSink
{
    void        finishPreparation(ByteBuffer src);
}