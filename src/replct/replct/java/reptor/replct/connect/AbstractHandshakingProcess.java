package reptor.replct.connect;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.connect.StandardHandshakeConnection;
import reptor.distrbt.com.handshake.HandshakeHandler;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.BufferedNetwork;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.jlib.collect.Slots;


public abstract class AbstractHandshakingProcess<R> implements HandshakingProcess<R>
{

    protected final short   m_procno;


    public AbstractHandshakingProcess(short procno)
    {
        m_procno = procno;
    }


    @Override
    public Slots<? extends HandshakeHandler>
            createHandlers(SchedulerContext<? extends SelectorDomainContext> master, short epno, int nhandlers)
    {
        return new Slots<>( nhandlers, i -> createHandler( master, epno, (short) i ) );
    }


    protected HandshakeHandler createHandler(SchedulerContext<? extends SelectorDomainContext> master, short epno, short hsno)
    {
        Handshake<?> handshake = createHandshake( master.getDomainContext(), epno, hsno );

        BufferedNetwork net = new BufferedNetwork( master.getDomainContext(), NetworkExtensions.ConnectionObserver.EMPTY,
                                                   handshake, this::networkBuffer );

        return new StandardHandshakeConnection<>( master, hsno, net, handshake );
    }


    protected ByteBuffer networkBuffer(int bufsize)
    {
        return ByteBuffer.allocateDirect( bufsize );
    }


    @Override
    public Object createConnectionArguments(RemoteEndpoint remep, InetSocketAddress addr)
    {
        return new StandardConnectionArguments( m_procno, remep, addr );
    }

}
