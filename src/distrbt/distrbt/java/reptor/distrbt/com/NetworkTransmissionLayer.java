package reptor.distrbt.com;

import java.io.IOException;

import reptor.chronos.com.CommunicationLayer;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.distrbt.com.handshake.HandshakeState;


public interface NetworkTransmissionLayer<II extends CommunicationSink, OI extends CommunicationSource,
                                          IO extends CommunicationSource, OO extends CommunicationSink>
        extends CommunicationLayer<II, OI, IO, OO>
{
    void    open(NetworkConnection<?, ?> conn, HandshakeState hsstate) throws IOException;
}
