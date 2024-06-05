package reptor.replct.connect;

import java.util.Objects;

import reptor.chronos.Immutable;
import reptor.distrbt.com.handshake.HandshakeState;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;


@Immutable
public class StandardHandshakeState extends HandshakeState
{

    private final RemoteEndpoint    m_remep;


    public StandardHandshakeState(RemoteEndpoint remep, boolean isconnector,
                                  BufferedNetworkState netstate, SslState sslstate)
    {
        super( isconnector, netstate, sslstate );

        m_remep = Objects.requireNonNull( remep );
    }


    public RemoteEndpoint getRemoteEndpoint()
    {
        return m_remep;
    }

}
