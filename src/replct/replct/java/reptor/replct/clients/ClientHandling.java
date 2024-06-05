package reptor.replct.clients;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.Ordering;
import reptor.replct.common.modules.AbstractProtocolComponent;
import reptor.replct.common.modules.WorkerRoutingMode;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.connect.Connections;
import reptor.replct.invoke.InvocationReplica;
import reptor.replct.map.Mapping;
import reptor.replct.secure.Cryptography;


public class ClientHandling extends AbstractProtocolComponent
{

    private final int[]                 m_defcliaddrs;

    private Cryptography                m_crypto;
    private Mapping                     m_map;
    private Connections                 m_connect;
    private Ordering                    m_order;

    private int[][]                     m_clint_to_orders;
    private int[][]                     m_clint_to_addrs;
    private int[][]                     m_clint_to_certifiers;
    private int[]                       m_clint_to_dom;
    private WorkerRoutingMode           m_cliroute = WorkerRoutingMode.DIRECT;


    public ClientHandling(int[] defcliaddrs)
    {
        m_defcliaddrs = Objects.requireNonNull( defcliaddrs );
    }


    public ClientHandling load(SettingsReader reader)
    {
        loadBasicSettings( reader, "clint" );

        m_cliroute            = WorkerRoutingMode.load( reader, "client.routing", m_cliroute );
        m_clint_to_addrs      = loadArrayMapping( reader, "clint", "addrs", m_defcliaddrs );
        m_clint_to_certifiers = loadArrayMapping( reader, "order", "worker", null );

        Preconditions.checkArgument( m_clint_to_addrs.length==m_nworkers );
        Preconditions.checkArgument( m_clint_to_certifiers.length==m_nworkers );

        m_clint_to_dom = reader.getIntArray( "schedulers.task.client_stage", null );

        if( m_clint_to_dom==null )
            throw new IllegalArgumentException();

        return this;
    }


    public ClientHandling cryptography(Cryptography crypto)
    {
        m_crypto = Objects.requireNonNull( crypto );

        return this;
    }


    public ClientHandling mapping(Mapping map)
    {
        m_map = Objects.requireNonNull( map );

        return this;
    }


    public ClientHandling connections(Connections connect)
    {
        m_connect = Objects.requireNonNull( connect );

        return this;
    }


    public ClientHandling ordering(Ordering order)
    {
        m_order = Objects.requireNonNull( order );

        return this;
    }


    public ClientHandling clientRouting(WorkerRoutingMode cliroute)
    {
        m_cliroute = cliroute;

        return this;
    }


    @Override
    public ClientHandling activate()
    {
        Preconditions.checkState( m_crypto!=null );
        Preconditions.checkState( m_map!=null );
        Preconditions.checkState( m_connect!=null );
        Preconditions.checkState( m_order!=null );

        super.activate();

        m_clint_to_orders = reverseIndex( m_order.getOrderToClientShardsMap(), m_nworkers );

        return this;
    }


    public ClientHandlingProcess createProcess(ReplicaPeerGroup repgroup, InvocationReplica invrep)
    {
        return new ClientHandlingProcess( this, repgroup, invrep );
    }


    public Cryptography getCryptography()
    {
        return m_crypto;
    }


    public Mapping getMapping()
    {
        return m_map;
    }


    public Connections getConnections()
    {
        return m_connect;
    }


    public Ordering getOrdering()
    {
        return m_order;
    }


    public int[] getOrderShardsForClientShard(int clintshard)
    {
        return m_clint_to_orders[ clintshard ];
    }


    public int[][] getAddressesForClientShardMap()
    {
        return m_clint_to_addrs;
    }


    public int[] getCertifierForClientShard(int clintshard)
    {
        return m_clint_to_certifiers[ clintshard ];
    }


    public int getDomainForClientShard(short shardno)
    {
        return m_clint_to_dom[ shardno % m_clint_to_dom.length ];
    }


    public WorkerRoutingMode getClientRoutingMode()
    {
        return m_cliroute;
    }


    public boolean useStandaloneClientShards()
    {
        return m_cliroute==WorkerRoutingMode.STANDALONE;
    }

}
