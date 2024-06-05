package reptor.distrbt.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.chronos.com.SynchronousSink;

// Notifies the master when the state changes from BLOCKED to WAIT_FOR_DATA or from BLOCKED to CAN_PROCESS or
// from WAIT_FOR_DATA to CAN_PROCESS. It is never marked as ready when the state is BLOCKED.
public interface UnbufferedDataSink extends SynchronousSink, UnbufferedEndpoint, GenericDataLinkElement
{
    UnbufferedDataSinkStatus canProcessData();
    // May be called even if canProcessData() returns false.
    void                     processData(ByteBuffer src) throws IOException;
}