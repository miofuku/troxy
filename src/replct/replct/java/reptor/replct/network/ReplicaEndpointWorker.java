package reptor.replct.network;

import java.util.List;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.connect.NetworkEndpointWorker;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.connect.CommunicationMessages.NewConnection;
import reptor.replct.connect.StandardHandshakeState;


public class ReplicaEndpointWorker extends NetworkEndpointWorker
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final ReplicaNetworkProcess                 m_netproc;

    private ReplicaPeers                                m_peers;
    private List<PushMessageSink<? super Message>>      m_clints;
    private List<PushMessageSink<? super Message>>      m_repnets;


    public ReplicaEndpointWorker(SchedulerContext<? extends SelectorDomainContext> master, ReplicaNetworkProcess netproc,
                                 String name, short epno, int nhandlers, HandshakeHandlerFactory handlfac, int recondelay)
    {
        super( master, name, epno, nhandlers, handlfac, recondelay);

        m_netproc = netproc;
    }


    //-------------------------------------//
    //             Configuration           //
    //-------------------------------------//

    public void initPeers(ReplicaPeers peers)
    {
        m_peers   = peers;
        m_clints  = peers.createChannels( peers.getClientShards(), domain().getDomainAddress() );
        m_repnets = peers.createChannels( peers.getReplicaNetworks(), domain().getDomainAddress() );
    }


    //-------------------------------------//
    //                 Actor               //
    //-------------------------------------//

    @Override
    public boolean execute()
    {
        super.execute();

        StandardHandshakeState hsstate;

        while( ( hsstate = (StandardHandshakeState) pollNewConnections() )!=null )
        {
            NewConnection newconn = new NewConnection( hsstate );

            short procno = hsstate.getRemoteEndpoint().getProcessNumber();

            if( procno<m_netproc.getReplicaGroup().size() )
                m_repnets.get( hsstate.getRemoteEndpoint().getNetworkNumber() ).enqueueMessage( newconn );
            else
                m_clints.get( m_peers.getClientShard( procno )).enqueueMessage( newconn );

            m_netproc.connectionEstablished( procno );
        }

        return isDone( !isReady() );
    }

}
