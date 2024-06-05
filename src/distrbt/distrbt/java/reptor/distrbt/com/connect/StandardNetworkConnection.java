package reptor.distrbt.com.connect;

import java.io.IOException;
import java.util.Objects;

import reptor.chronos.com.PushMessageSource;
import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkTransmissionLayer;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.com.map.NetworkMessageSink;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelTask;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.link.BufferedToUnbufferedLink;
import reptor.distrbt.io.link.UnbufferedToBufferedLink;
import reptor.distrbt.io.net.BufferedNetwork;
import reptor.distrbt.io.net.Network;


public class StandardNetworkConnection
        extends AbstractNetworkTransmissionConnection<PushMessageSource<NetworkMessage>, NetworkMessageSink>
        implements PushNetworkTransmissionConnection
{

    private final BufferedNetwork                   m_net;

    private final NetworkTransmissionLayer<? extends UnbufferedDataSink, ? extends UnbufferedDataSource,
                                           ? extends PushMessageSource<NetworkMessage>, ? extends NetworkMessageSink>
                                                    m_conn;

    private final DataChannelTask                   m_outchannel;
    private final DataChannelTask                   m_inchannel;


    public StandardNetworkConnection(int connid, SocketChannelConfiguration config, BufferedNetwork net,
                                     NetworkTransmissionLayer<? extends UnbufferedDataSink, ? extends UnbufferedDataSource,
                                             ? extends PushMessageSource<NetworkMessage>, ? extends NetworkMessageSink> conn)
    {
        this( connid, config, net, conn,
                new BufferedToUnbufferedLink( net.getInbound(), conn.getInboundConnect() ),
                new UnbufferedToBufferedLink( conn.getOutboundConnect(), net.getOutbound() ) );
    }


    public StandardNetworkConnection(int connid, SocketChannelConfiguration config, BufferedNetwork net,
                                     NetworkTransmissionLayer<? extends UnbufferedDataSink, ? extends UnbufferedDataSource,
                                             ? extends PushMessageSource<NetworkMessage>, ? extends NetworkMessageSink> conn,
                                     DataChannelTask inchannel, DataChannelTask outchannel)
    {
        super( connid, config );

        m_net  = Objects.requireNonNull( net );
        m_conn = Objects.requireNonNull( conn );

        m_inchannel  = Objects.requireNonNull( inchannel );
        m_outchannel = Objects.requireNonNull( outchannel );
    }


    @Override
    protected Network net()
    {
        return m_net.getNetwork();
    }


    @Override
    public void bindToMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        super.bindToMaster( master );

        m_inchannel.bindToMaster( this );
        m_outchannel.bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        m_inchannel.unbindFromMaster( this );
        m_outchannel.unbindFromMaster( this );

        super.unbindFromMaster( master );
    }


    @Override
    protected void doOpen(HandshakeState hsstate) throws IOException
    {
        m_net.installState( hsstate.getBufferedNetworkState() );

        m_conn.open( this, hsstate );
        m_net.adjustBuffer( m_conn );

        m_conn.activate();
        m_net.activate();
    }


    @Override
    public PushMessageSource<NetworkMessage> getInbound()
    {
        return m_conn.getInbound();
    }


    @Override
    public NetworkMessageSink getOutbound()
    {
        return m_conn.getOutbound();
    }


    @Override
    public boolean execute()
    {
        return executeChannelsCatched( m_outchannel, m_inchannel );
    }

}