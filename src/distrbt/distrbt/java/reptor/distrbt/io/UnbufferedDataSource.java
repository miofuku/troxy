package reptor.distrbt.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.SynchronousSource;


public interface UnbufferedDataSource extends SynchronousSource, UnbufferedEndpoint, GenericDataLinkElement
{
    static final int UNSUPPORTED_OPERATION = Integer.MIN_VALUE;
    static final int NO_PENDING_DATA       = Integer.MAX_VALUE;

    // Integer.MIN_VALUE -> unsupported operation, Integer.MAX_VALUE -> no pending data,
    // >=0 -> currently required minimum buffer size for pending data
    int         getRequiredBufferSize();
    boolean     canRetrieveData(boolean hasremaining, int bufsize);
    void        retrieveData(ByteBuffer dst) throws IOException;
}