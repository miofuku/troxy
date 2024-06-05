package reptor.distrbt.com.connect;

import java.util.function.IntFunction;

import reptor.chronos.Immutable;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.map.PushMessageEncoding;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.BufferedDataSink;
import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.DataChannelTask;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.channel.BinaryBufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryBufferedSourceChannel;
import reptor.distrbt.io.channel.BinaryUnbufferedSinkChannel;
import reptor.distrbt.io.channel.BinaryUnbufferedSourceChannel;
import reptor.distrbt.io.link.AdaptiveToAdaptiveLink;
import reptor.distrbt.io.link.BufferedToUnbufferedLink;
import reptor.distrbt.io.link.UnbufferedToBufferedLink;
import reptor.distrbt.io.net.BufferedNetwork;
import reptor.distrbt.io.net.BufferedToNetworkLink;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.distrbt.io.ssl.Ssl;


@Immutable
public class StandardConnectionConfiguration extends AbstractNetworkTransmissionConnectionConfiguration
{

    private final boolean            m_alwayscopy;
    private final boolean            m_bind_ssl_to_net_out = false;
    private final boolean            m_bind_ssl_to_net_in  = true;


    public StandardConnectionConfiguration(ConnectionObserver observer, int recvbufsize, int sendbufsize, boolean usetcpnodelay,
                                           boolean enablessl, boolean alwayscopy)
    {
        super( observer, recvbufsize, sendbufsize, usetcpnodelay, enablessl );

        m_alwayscopy    = alwayscopy;
    }


    @Override
    public PushNetworkTransmissionConnection connection(SelectorDomainContext domcntxt, int connid,
                                                        MessageMapper mapper, IntFunction<Object> msgcntxtfac)
    {
        // Layers
        BufferedNetwork net = new BufferedNetwork( domcntxt, m_observer,
                                                   m_recvbufsize, !m_enablessl && m_sendbufsize==0 ? -1 : m_sendbufsize,
                                                   this::networkBuffer );
        PushMessageEncoding msgenc = new PushMessageEncoding( mapper, connid, msgcntxtfac );

        Ssl ssl = !m_enablessl ? null : new Ssl( domcntxt, null );

        // Outbound channel
        DataChannelTask outchannel;

        if( ssl!=null )
        {
            ssl.adjustBuffer( m_recvbufsize, m_sendbufsize );

            UnbufferedDataSource outsrc;
            BufferedDataSink     outsnk = net.getNetworkBuffering().getOutbound();

            if( m_bind_ssl_to_net_out )
            {
                outsrc = msgenc.getOutbound();
                outsnk = new BinaryBufferedSinkChannel( ssl.getOutboundStage(), outsnk );
            }
            else
            {
                if( m_alwayscopy )
                    outsrc = new BinaryUnbufferedSourceChannel( (UnbufferedDataSource) msgenc.getOutbound(), ssl.getOutboundStage() );
                else
                    outsrc = new BinaryUnbufferedSourceChannel( msgenc.getOutbound(), ssl.getOutboundStage() );
            }

            outchannel = new UnbufferedToBufferedLink( outsrc, outsnk );
        }
        else if( m_sendbufsize==0 )
        {
            outchannel = new BufferedToNetworkLink( msgenc.getOutbound(), net.getNetwork().getOutbound() );
        }
        else
        {
            if( m_alwayscopy )
                outchannel = new UnbufferedToBufferedLink( msgenc.getOutbound(), net.getNetworkBuffering().getOutbound() );
            else
                outchannel = new AdaptiveToAdaptiveLink( msgenc.getOutbound(), net.getNetworkBuffering().getOutbound() );
        }

        // Inbound channel
        BufferedDataSource insrc = net.getNetworkBuffering().getInbound();
        UnbufferedDataSink insnk = msgenc.getInbound();

        if( ssl!=null )
        {
            if( m_bind_ssl_to_net_in )
                insrc = new BinaryBufferedSourceChannel( insrc, ssl.getInboundStage() );
            else
                insnk = new BinaryUnbufferedSinkChannel( ssl.getInboundStage(), insnk );
        }

        DataChannelTask inchannel = new BufferedToUnbufferedLink( insrc, insnk );

        // Connection
//        return new StandardNetworkConnection( master, connid, this, net, ssl, msgenc, inchannel, outchannel );
        if( ssl==null )
            return new StandardNetworkConnection( connid, this, net, msgenc, inchannel, outchannel );
        else
            return new StandardNetworkConnection( connid, this, net, new SslNetworkConnector( net, ssl, msgenc ),
                                                  inchannel, outchannel );
    }

}
