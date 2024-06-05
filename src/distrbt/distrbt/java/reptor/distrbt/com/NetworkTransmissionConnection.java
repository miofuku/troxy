package reptor.distrbt.com;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AttachableOrphic;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.domains.SelectorDomainContext;


public interface NetworkTransmissionConnection<I extends CommunicationSource, O extends CommunicationSink>
        extends NetworkConnection<I, O>, AttachableOrphic<SchedulerContext<? extends SelectorDomainContext>>
{
    @Notifying
    void    open(HandshakeState hsstate);
    boolean isOpen();
}
