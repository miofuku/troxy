package reptor.replct.network;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.replct.RemoteReplica;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Connections;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.invoke.Invocation;
import reptor.replct.invoke.InvocationReplica;
import reptor.replct.map.Mapping;


public class Network
{

    private final byte              m_nreplicas;

    private RemoteReplica[]         m_replicas;
    private int                     m_nrepeps;
    private int                     m_nclieps;
    private int[]                   m_clieps;
    private int                     m_nrepnets;
    private int[]                   m_repep_to_nnets;
    private int[]                   m_nets_to_dom;
    private int                     m_nephandlers   = 2;
    private int                     m_recondelay    = 100; // in ms

    private Mapping                 m_map;
    private Connections             m_connect;
    private Invocation              m_invoke;

    private boolean                 m_isactive = false;


    public Network(byte nreplicas)
    {
        m_nreplicas = nreplicas;
    }


    public Network load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        m_nrepeps = reader.getInt( "networks.replica.addrs", 1 );
        m_nclieps = reader.getInt( "networks.replica.client_addrs", 0 );

        int[] repeps = new int[ m_nrepeps ];
        Arrays.setAll( repeps, repaddrno -> repaddrno );

        if( m_nclieps==0 )
            m_clieps = repeps;
        else
        {
            m_clieps = new int[ m_nclieps ];
            Arrays.setAll( m_clieps, cliaddrno -> cliaddrno+m_nrepeps );
        }

        m_replicas = new RemoteReplica[ m_nreplicas ];

        for( byte repno=0; repno<m_nreplicas; repno++ )
        {
            InetSocketAddress[] addrs   = new InetSocketAddress[ m_nrepeps+m_nclieps ];
            int                 curaddr = 0;

            for( int addrno=0; addrno<m_nrepeps; addrno++ )
                addrs[ curaddr++ ] = reader.getAddress( "addresses.server." + repno + "." + addrno );

            if( m_nclieps>0 )
            {
                for( int addrno=0; addrno<m_nclieps; addrno++ )
                    addrs[ curaddr++ ] = reader.getAddress( "addresses.server." + repno + ".clients." + addrno );
            }

            m_replicas[ repno ] = new RemoteReplica( repno, addrs, repeps, m_clieps );
        }

        m_nrepnets       = 0;
        m_repep_to_nnets = new int[ m_nrepeps+m_nclieps ];

        for( int epno=0; epno<m_nrepeps; epno++ )
        {
            m_repep_to_nnets[ epno ] = reader.getInt( "networks.replica.number." + epno, 1 );
            m_nrepnets += m_repep_to_nnets[ epno ];
        }

        m_nets_to_dom = reader.getIntArray( "schedulers.task.replica_network_endpoint_worker.group", null );

        if( m_nets_to_dom==null )
            throw new IllegalArgumentException();

        return this;
    }


    public RemoteReplica[] getReplicas()
    {
        return m_replicas;
    }


    public Network connections(Connections connect)
    {
        m_connect = Objects.requireNonNull( connect );

        return this;
    }


    public Network invocation(Invocation invoke)
    {
        m_invoke = Objects.requireNonNull( invoke );

        return this;
    }


    public Network mapping(Mapping map)
    {
        m_map = Objects.requireNonNull( map );

        return this;
    }


    public Network activate()
    {
        Preconditions.checkState( !m_isactive );
        Preconditions.checkState( m_map!=null );
        Preconditions.checkState( m_connect!=null );
        Preconditions.checkState( m_invoke!=null );

        m_isactive = true;

        return this;
    }


    public ReplicaNetworkProcess createReplicaProcess(ReplicaPeerGroup repgroup, InvocationReplica invrep)
    {
        Preconditions.checkState( m_isactive );

        HandshakingProcess<?> stdhs = invrep.getHandshake();
        HandshakingProcess<?> rephs = m_nclieps>0 ? m_connect.getHandshakeForReplicas().createProcess( repgroup.getReplicaNumber() ) :
                                                    stdhs;

        return new ReplicaNetworkProcess( this, repgroup, stdhs, rephs );
    }


    public Connections getConnections()
    {
        return m_connect;
    }


    public Invocation getInvocation()
    {
        return m_invoke;
    }


    public int getNumberOfReplicaEndpoints()
    {
        return m_repep_to_nnets.length;
    }


    public int getNumberOfReplicaNetworks()
    {
        return m_nrepnets;
    }


    public int getNumberOfHandlersPerEndpoint()
    {
        return m_nephandlers;
    }


    public int getNumberOfNetworksForEndpoint(int epno)
    {
        return m_repep_to_nnets[ epno ];
    }


    public int getEndpointForNetwork(int repnetno)
    {
        for( int epno=0, total=0; epno<m_repep_to_nnets.length; epno++ )
        {
            total += m_repep_to_nnets[ epno ];

            if( repnetno<total )
                return epno;
        }

        throw new IllegalArgumentException();
    }


    public boolean isEndpointForClients(int epno)
    {
        Preconditions.checkElementIndex( epno, m_nrepeps+m_nclieps );

        return m_nclieps==0 || epno>=m_nrepeps;
    }


    public int getNumberOfEndpointsForClients()
    {
        return m_nclieps>0 ? m_nclieps : m_nrepeps;
    }


    public short getHandlerID(short epno, short handlno)
    {
        int off = m_nclieps>0 ? m_nrepeps : 0;
        return (short) ( ( epno-off )*m_nephandlers+handlno );
    }


    public int getConnectionRetryDelay()
    {
        return m_recondelay;
    }


    public Handshaking<?> getHandshakeProtocolForEndpoint(int epno)
    {
        return isEndpointForClients( epno ) ? m_invoke.getReplicaHandshake() : m_connect.getHandshakeForReplicas();
    }


    public int[] getClientEndpoints()
    {
        return m_clieps;
    }


    public Mapping getMapping()
    {
        return m_map;
    }


    public int getDomainForReplicaEndpoint(int epno)
    {
        return 0;
    }


    public int getDomainForReplicaNetwork(int netno)
    {
        return m_nets_to_dom[ netno % m_nets_to_dom.length ];
    }

}
