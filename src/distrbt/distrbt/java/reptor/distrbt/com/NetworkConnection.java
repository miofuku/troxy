package reptor.distrbt.com;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.ConnectionEndpoint;


public interface NetworkConnection<I extends CommunicationSource, O extends CommunicationSink>
        extends ChronosTask, ConnectionEndpoint<I, O>
{

}
