package reptor.distrbt.io;

import java.io.IOException;
import java.nio.ByteBuffer;


public interface AdaptiveDataSource extends BufferedDataSource, UnbufferedDataSource
{
    boolean     canProcessData(boolean hasremaining, int bufsize);
    // Serves as BufferedDataSource.execute() + startDataProcessing()
    // and as UnbufferedDataSource.retrieveData()
    ByteBuffer  startDataProcessing(ByteBuffer dst) throws IOException;
}