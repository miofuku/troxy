package reptor.replct.network;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import reptor.chronos.link.MulticastLink;
import reptor.chronos.link.RoundRobinMulticastLink;
import reptor.chronos.schedule.GenericScheduler;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.ConnectionConfiguration;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.connect.RemoteEndpoint;


public class ReplicaNetworkProcess
{

    private final Network               m_network;
    private final ReplicaPeerGroup      m_repgroup;
    private final HandshakingProcess<?> m_stdhs;    // Used for endpoints for clients and replica connections.
    private final HandshakingProcess<?> m_rephs;    // Used for endpoints dedicated to replica connections.

    private ConnectionObserver      m_connobs;
    private ReplicaEndpointWorker[] m_repeps;
    private ReplicaNetworkWorker[]  m_repnets;
    private CountDownLatch          m_repconnsready;


    public ReplicaNetworkProcess(Network network, ReplicaPeerGroup repgroup, HandshakingProcess<?> stdhs, HandshakingProcess<?> rephs)
    {
        m_network   = Objects.requireNonNull( network );
        m_repgroup  = Objects.requireNonNull( repgroup );
        m_stdhs     = Objects.requireNonNull( stdhs );
        m_rephs     = rephs;
    }


    public ReplicaNetworkProcess connectionObserver(ConnectionObserver connobs)
    {
        m_connobs = Objects.requireNonNull( connobs );

        return this;
    }


    public ReplicaNetworkProcess initWorkers(List<? extends GenericScheduler<SelectorDomainContext>> scheds)
    {
        initEndpoints( scheds );
        initNetworks( scheds );

        m_repconnsready = new CountDownLatch( m_network.getNumberOfReplicaNetworks()*( m_repgroup.size()-1 ) );

        return this;
    }


    private void initEndpoints(List<? extends GenericScheduler<SelectorDomainContext>> scheds)
    {
        m_repeps = new ReplicaEndpointWorker[ m_network.getNumberOfReplicaEndpoints() ];

        for( short epno=0; epno<m_repeps.length; epno++ )
        {
            GenericScheduler<SelectorDomainContext> sched = scheds.get( m_network.getDomainForReplicaEndpoint( epno ) );

            m_repeps[ epno ] = new ReplicaEndpointWorker( sched.getContext(), this, "REP" + epno, epno,
                    m_network.getNumberOfHandlersPerEndpoint(), getHandshake( epno)::createHandlers, m_network.getConnectionRetryDelay() );
            sched.registerTask( m_repeps[ epno ] );
        }
    }


    private HandshakingProcess<?> getHandshake(short epno)
    {
        return m_network.isEndpointForClients( epno ) ? m_stdhs : m_rephs;
    }


    private void initNetworks(List<? extends GenericScheduler<SelectorDomainContext>> scheds)
    {
        m_repnets = new ReplicaNetworkWorker[ m_network.getNumberOfReplicaNetworks() ];

        ConnectionConfiguration repconconf = m_network.getConnections().createReplicaConnectionConfiguration( m_connobs );

        for( short netno=0; netno<m_repnets.length; netno++ )
        {
            GenericScheduler<SelectorDomainContext> sched = scheds.get( m_network.getDomainForReplicaNetwork( netno ) );
            MessageMapper mapper = m_network.getMapping().createReplicaMessageMapper();

            m_repnets[ netno ] = new ReplicaNetworkWorker( sched.getContext(), netno, m_repgroup, repconconf.connectionProvider( mapper ));
            sched.registerTask( m_repnets[ netno ] );
        }
    }


    public ReplicaEndpointWorker[] getEndpoints()
    {
        return m_repeps;
    }


    public ReplicaNetworkWorker[] getNetworks()
    {
        return m_repnets;
    }


    public MulticastLink<NetworkMessage> createTransmissionChannel(int[] nets)
    {
        if( nets.length==1 )
            return m_repnets[ nets[ 0 ] ].getReplicaGroupChannel();
        else
        {
            List<MulticastLink<? super NetworkMessage>> channels = new ArrayList<>( nets.length );
            for( int i=0; i<nets.length; i++ )
                channels.add( m_repnets[ nets[ i ] ].getReplicaGroupChannel() );

            return new RoundRobinMulticastLink<>( channels );
        }
    }


    public ReplicaNetworkProcess initPeers(ReplicaPeers peers)
    {
        for( ReplicaEndpointWorker ep : m_repeps )
            ep.initPeers( peers );

        for( ReplicaNetworkWorker net : m_repnets )
            net.initPeers( peers );

        return this;
    }


    public ReplicaNetworkProcess start()
    {
        openEndpoints();
        startConnections();

        return this;
    }


    private void openEndpoints()
    {
        for( short addrno=0; addrno<m_repeps.length; addrno++ )
            m_repeps[ addrno ].open( m_repgroup.getAddress( addrno ) );
    }


    private void startConnections()
    {
        for( short epno=0, netno=0; epno<m_repeps.length; epno++ )
        {
            for( short i=0; i<m_network.getNumberOfNetworksForEndpoint( epno ); i++, netno++ )
            {
                for( byte remno = (byte) ( m_repgroup.getReplicaNumber()+1 ); remno<m_repgroup.size(); remno++ )
                {
                    InetSocketAddress addr = m_repgroup.getReplica( remno ).getAddressForReplicas( epno );

                    Object args = getHandshake( epno ).createConnectionArguments( new RemoteEndpoint( remno, netno ), addr );

                    m_repeps[ epno ].startConnection( args );
                }
            }
        }
    }


    public ReplicaPeerGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    public void connectionEstablished(short procno)
    {
        if( procno<m_repgroup.size() )
            m_repconnsready.countDown();
    }


    public void awaitReplicaConnections() throws InterruptedException
    {
        m_repconnsready.await();
    }

}
