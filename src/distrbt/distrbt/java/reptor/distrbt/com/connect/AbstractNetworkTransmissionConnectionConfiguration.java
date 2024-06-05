package reptor.distrbt.com.connect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.function.IntFunction;

import reptor.chronos.Immutable;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;


@Immutable
public abstract class AbstractNetworkTransmissionConnectionConfiguration implements ConnectionConfiguration, SocketChannelConfiguration
{

    protected final ConnectionObserver m_observer;
    protected final int                m_recvbufsize;
    protected final int                m_sendbufsize;
    protected final boolean            m_usetcpnodelay;
    protected final boolean            m_enablessl;

    public AbstractNetworkTransmissionConnectionConfiguration(ConnectionObserver observer, int recvbufsize, int sendbufsize,
                                                         boolean usetcpnodelay, boolean enablessl)
    {
        m_observer      = Objects.requireNonNull( observer );
        m_recvbufsize   = recvbufsize;
        m_sendbufsize   = sendbufsize;
        m_usetcpnodelay = usetcpnodelay;
        m_enablessl     = enablessl;
    }


    @Override
    public NetworkConnectionProvider connectionProvider(MessageMapper mapper)
    {
        return new Provider( mapper );
    }


    private class Provider implements NetworkConnectionProvider
    {
        private final MessageMapper m_mapper;

        public Provider(MessageMapper mapper)
        {
            m_mapper = Objects.requireNonNull( mapper );
        }

        @Override
        public PushNetworkTransmissionConnection connection(SelectorDomainContext domcntxt, int connid, IntFunction<Object> msgcntxtfac)
        {
            return AbstractNetworkTransmissionConnectionConfiguration.this.connection( domcntxt, connid, m_mapper, msgcntxtfac );
        }
    }


    public abstract PushNetworkTransmissionConnection connection(SelectorDomainContext domcntxt, int connid,
                                                                 MessageMapper mapper, IntFunction<Object> msgcntxtfac);


    public ByteBuffer networkBuffer(int bufsize)
    {
        return ByteBuffer.allocateDirect( bufsize );
    }


    @Override
    public void configureChannel(SocketChannel channel) throws IOException
    {
        channel.socket().setTcpNoDelay( m_usetcpnodelay );
    }


    public ConnectionObserver getConnectionObserver()
    {
        return m_observer;
    }


    public int getNetworkReceiveBufferSize()
    {
        return m_recvbufsize;
    }


    public int getNetworkTransmitBufferSize()
    {
        return m_sendbufsize;
    }


    public boolean getUseTcpNoDelay()
    {
        return m_usetcpnodelay;
    }


    public boolean enableSsl()
    {
        return m_enablessl;
    }

}
