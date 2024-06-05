package reptor.replct.connect;

import java.net.InetSocketAddress;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.Slots;


public interface HandshakingProcess<R>
{
    Handshake<? extends R>  createHandshake(SelectorDomainContext domcntxt, short epno, short hsno);

    Slots<? extends HandshakeHandler>
            createHandlers(SchedulerContext<? extends SelectorDomainContext> master, short epno, int nhandlers);

    Object createConnectionArguments(RemoteEndpoint remep, InetSocketAddress addr);
}
