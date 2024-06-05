package reptor.replct.agree.order;

import com.google.common.base.Preconditions;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.NetworkProtocolComponent;
import reptor.replct.agree.order.OrderMessages.CommandBatch;
import reptor.replct.agree.order.OrderMessages.Noop;
import reptor.replct.common.WorkDistribution;
import reptor.replct.common.modules.AbstractProtocolComponent;
import reptor.replct.common.settings.SettingsReader;


public abstract class Ordering extends AbstractProtocolComponent implements NetworkProtocolComponent
{

    protected final int             m_nreplicas;
    protected final int             m_chkptint;

    protected int                   m_commitquorum;
    protected boolean               m_passiveprogress       = true;
    protected int                   m_minbatchsize          = 1;
    protected int                   m_maxbatchsize          = 1;

    protected int                   m_forcedvcinterval      = 0;
    protected int                   m_forcedvctimeout       = 1000;

    protected int                   m_orderwndchkptfac      = 5;
    protected int                   m_actorderwndchkptfac   = 3;
    protected boolean               m_unboundorderwnd       = true;
    protected int                   m_orderwnd;
    protected int                   m_orderwndshard;
    protected int                   m_actorderwnd;
    protected int                   m_actorderwndshard;

    protected boolean               m_rotate                = false;
    protected WorkDistribution  m_propdist;
    protected WorkDistribution  m_orderdist;

    protected int[][]               m_order_to_nets;
    protected int[][]               m_order_to_workers;
    protected int[][]               m_order_to_clints;
    protected int[]                 m_order_to_exect;


    public Ordering(int nreplicas, int chkptint, int defquorumsize)
    {
        m_nreplicas = nreplicas;
        m_chkptint  = chkptint;
        m_commitquorum = defquorumsize;
    }


    public Ordering load(SettingsReader reader)
    {
        loadBasicSettings( reader, "order" );

        m_commitquorum = reader.getInt( "agreement.commit_threshold", m_commitquorum );
        Preconditions.checkArgument( m_commitquorum>0 && m_commitquorum<=m_nreplicas );

        m_minbatchsize = reader.getInt( "agreement.batchsize_min", m_minbatchsize );
        m_maxbatchsize = reader.getInt( "agreement.batchsize_max", m_maxbatchsize );
        Preconditions.checkArgument( m_minbatchsize>0 && m_maxbatchsize>=m_minbatchsize );

        m_forcedvcinterval = reader.getInt( "benchmark.viewchange_interval", m_forcedvcinterval );

        m_actorderwndchkptfac = reader.getInt( "agreement.active_window_factor", m_actorderwndchkptfac );
        m_orderwndchkptfac    = reader.getInt( "agreement.full_window_factor", m_orderwndchkptfac );
        Preconditions.checkArgument( m_actorderwndchkptfac>0 && m_orderwndchkptfac>=m_actorderwndchkptfac  );

        String rotval = reader.getString( "agreement.rotate", null );
        if( rotval!=null )
        {
            if( rotval.equals( "false" ) )
                m_rotate = false;
            else
            {
                m_rotate   = true;
                m_propdist = parseInstanceDistribution( rotval, m_nreplicas, m_nworkers );
            }
        }

        String distname = reader.getString( "agreement.inst_dist", null );
        if( distname!=null )
            m_orderdist = parseInstanceDistribution( distname, m_nworkers, m_nreplicas );

        m_order_to_nets    = loadArrayMapping( reader, "order", "network", new int[] { 0 } );
        m_order_to_workers = loadArrayMapping( reader, "order", "worker", null );
        m_order_to_clints  = loadArrayMapping( reader, "order", "client", new int[] { 0 } );
        m_order_to_exect   = loadMapping( reader, "order", "exect", 0 );

        return this;
    }


    private WorkDistribution parseInstanceDistribution(String name, int nworkers, int blocksize)
    {
        switch( name )
        {
        case "rr":
            return new WorkDistribution.RoundRobin( nworkers );
        case "block":
            return new WorkDistribution.Blockwise( nworkers, blocksize );
        case "skew":
            return new WorkDistribution.Skewed( nworkers );
        default:
            throw new IllegalArgumentException( "Unknown instance distribution: " + name );
        }
    }


    @Override
    public Ordering activate()
    {
        super.activate();

        if( m_orderdist==null )
            m_orderdist = new WorkDistribution.RoundRobin( m_nworkers );

        int period = m_orderdist.getPeriodLength();
        int odrwnd = m_chkptint * m_orderwndchkptfac;
        m_orderwnd      = odrwnd + (period - odrwnd % period) % period;
        m_orderwndshard = m_orderwnd / m_nworkers;

        int actwnd = m_chkptint * m_actorderwndchkptfac;
        m_actorderwnd      = actwnd + (period - actwnd % period) % period;
        m_actorderwndshard = m_actorderwnd / m_nworkers;

        if( m_order_to_nets==null )
            m_order_to_nets = uniformArrayMapping( 0 );

        if( m_order_to_clints==null )
            m_order_to_clints = uniformArrayMapping( 0 );

        if( m_order_to_exect==null )
            m_order_to_exect = uniformMapping( 0 );

        return this;
    }


    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        msgreg.addMessageType( OrderMessages.NOOP_ID, Noop::new )
              .addMessageType( OrderMessages.COMMAND_BATCH_ID, CommandBatch::new );
    }


    public int getCommitQuorumSize()
    {
        return m_commitquorum;
    }


    public boolean getUsePassiveProgress()
    {
        return m_passiveprogress;
    }


    public boolean getUseRotatingLeader()
    {
        return m_rotate;
    }


    // TODO: OrderShards should cache this information.
    public byte getCoordinator(int viewno)
    {
        return (byte) ( viewno % m_nreplicas );
    }


    public byte getProposer(int viewno, long orderno)
    {
        if( !m_rotate )
            return getCoordinator( viewno );
        else
        {
            int coord = getCoordinator( viewno );
            int prop  = m_propdist.getStageForUnit( orderno );

            return (byte) ( (prop + coord) % m_nreplicas );
        }
    }


    public byte getContact(int viewno, byte repno)
    {
        return m_rotate ? repno : getCoordinator( viewno );
    }


    public int getForcedViewChangeInterval()
    {
        return m_forcedvcinterval;
    }


    public int getForcedViewChangeTimeout()
    {
        return m_forcedvctimeout;
    }


    public int getOrderWindowSize()
    {
        return m_orderwnd;
    }


    public int getOrderWindowSizeForShard()
    {
        return m_orderwndshard;
    }


    public int getActiveOrderWindowSize()
    {
        return m_actorderwnd;
    }


    public int getActiveOrderWindowSizeForShard()
    {
        return m_actorderwndshard;
    }


    public boolean getUseUnboundOrderWindow()
    {
        return m_unboundorderwnd;
    }


    public int getMinumumCommandBatchSize()
    {
        return m_minbatchsize;
    }


    public int getMaximumCommandBatchSize()
    {
        return m_maxbatchsize;
    }


    public WorkDistribution getOrderInstanceShardDistribution()
    {
        return m_orderdist;
    }


    public int[] getLinkedNetworksForOrderShard(int ordershard)
    {
        return m_order_to_nets[ ordershard ];
    }


    public int[] getLinkedWorkersForOrderShard(int ordershard)
    {
        return m_order_to_workers[ ordershard ];
    }


    public int[] getLinkedClientShardsForOrderShard(int ordershard)
    {
        return m_order_to_clints[ ordershard ];
    }


    public int getLinkedExecutorForOrderShard(int ordershard)
    {
        return m_order_to_exect[ ordershard ];
    }


    public int[][] getOrderToClientShardsMap()
    {
        return m_order_to_clints;
    }

}
