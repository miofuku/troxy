package reptor.distrbt.io.net;

import java.nio.ByteBuffer;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationLayerElement;
import reptor.chronos.com.SynchronousEndpoint;
import reptor.distrbt.io.ExternalizableDataElement;
import reptor.distrbt.io.GenericDataLinkElement;


public interface NetworkEndpointBuffer extends CommunicationLayerElement, SynchronousEndpoint, GenericDataLinkElement, ExternalizableDataElement
{
    int         getCapacity();

    // newbuffer must be clear. Returns the old buffer in an undefined state.
    @Notifying
    ByteBuffer  replaceBuffer(ByteBuffer newbuffer);

    void        clear();
}
