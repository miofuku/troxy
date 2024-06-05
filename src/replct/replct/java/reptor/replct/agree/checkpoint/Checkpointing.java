package reptor.replct.agree.checkpoint;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.NetworkProtocolComponent;
import reptor.replct.agree.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.common.WorkDistribution;
import reptor.replct.common.modules.AbstractProtocolComponent;
import reptor.replct.common.settings.SettingsReader;


public class Checkpointing extends AbstractProtocolComponent implements NetworkProtocolComponent
{

    // TODO: Get rid of it. Can be done when the workers are created.
    protected final boolean         m_multiexect;

    protected CheckpointMode        m_chkptmode         = CheckpointMode.REGULAR;
    protected int                   m_chkptquorum;
    protected int                   m_chkptint          = 100;
    protected boolean               m_passiveprogress   = true;
    protected WorkDistribution  m_coordselect;
    protected boolean               m_hashedchkpts      = false;
    protected boolean               m_multicastcreate   = false;
    protected boolean               m_proptoall         = true;

    protected int[][]               m_chkpt_to_nets;
    protected int[]                 m_chkpt_to_exect;


    public Checkpointing(boolean multiexect, int defquorumsize)
    {
        m_multiexect  = multiexect;
        m_chkptquorum = defquorumsize;
    }


    public Checkpointing load(SettingsReader reader)
    {
        loadBasicSettings( reader, "chkpt" );

        String chkptmode = reader.getString( "agreement.checkpoint_mode", null );
        if( chkptmode!=null )
            m_chkptmode = parseCheckpointMode( chkptmode );

        if( !m_chkptmode.includes( CheckpointMode.SEND ))
            m_chkptquorum = 1;
        else
            m_chkptquorum = reader.getInt( "agreement.checkpoint_threshold", m_chkptquorum );

        m_chkptint        = reader.getInt( "agreement.checkpoint_interval", m_chkptint );
        m_passiveprogress = reader.getBool( "agreement.passive_checkpoints", m_passiveprogress );

        m_proptoall      = reader.getBool( getKey( "chkpt", "toall" ), m_proptoall );
        m_chkpt_to_nets  = loadArrayMapping( reader, "chkpt", "network", new int[] { 0 } );
        m_chkpt_to_exect = loadMapping( reader, "chkpt", "exect", 0 );

        return this;
    }


    private CheckpointMode parseCheckpointMode(String name)
    {
        switch( name )
        {
        case "regular":
            return CheckpointMode.REGULAR;
        case "nodeprogress":
            return CheckpointMode.NODE_PROGRESS;
        case "apply":
            return CheckpointMode.APPLY;
        case "send":
            return CheckpointMode.SEND;
        case "create":
            return CheckpointMode.CREATE;
        default:
            throw new IllegalArgumentException( "Unknown checkpoint mode: " + name );
        }
    }


    @Override
    public Checkpointing activate()
    {
        super.activate();

        m_coordselect = m_nworkers==1 ? new WorkDistribution.Continuous( 0 ) :
                                        new WorkDistribution.Blockwise( m_nworkers, m_chkptint );

        return this;
    }


    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        msgreg.addMessageType( CheckpointMessages.CHECKPOINT_ID, Checkpoint::new );
    }


    public CheckpointMode getCheckpointMode()
    {
        return m_chkptmode;
    }


    public int getCheckpointInterval()
    {
        return m_chkptint;
    }


    public int getCheckpointQuorumSize()
    {
        return m_chkptquorum;
    }


    public boolean usePassiveProgress()
    {
        return m_passiveprogress;
    }


    public boolean useHashedCheckpoints()
    {
        return m_hashedchkpts;
    }


    public boolean getMulticastCheckpointCreation()
    {
        return m_multicastcreate;
    }


    public boolean useCombinedSnapshots()
    {
        return m_multiexect && m_proptoall;
    }


    public WorkDistribution getCoordinatorSelection()
    {
        return m_coordselect;
    }


    public int[] getLinkedNetworksForCheckpointShard(int chkptshard)
    {
        return m_chkpt_to_nets[ chkptshard ];
    }


    public int getLinkedExecutorForCheckpointShard(int chkptshard)
    {
        return m_chkpt_to_exect[ chkptshard ];
    }


    public int[] getCheckpointShardToExecutorMap()
    {
        return m_chkpt_to_exect;
    }


    public boolean getPropogateToAll()
    {
        return m_proptoall;
    }

}
