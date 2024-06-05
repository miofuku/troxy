package reptor.distrbt.com.connect;

import reptor.chronos.com.PushMessageSource;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkTransmissionConnection;
import reptor.distrbt.com.map.NetworkMessageSink;


// This is actually a push-based network connection.
public interface PushNetworkTransmissionConnection
        extends NetworkTransmissionConnection<PushMessageSource<NetworkMessage>, NetworkMessageSink>, Actor
{

}
