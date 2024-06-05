package reptor.replct.execute;

import java.util.Objects;

import reptor.replct.common.WorkDistribution;
import reptor.replct.common.modules.AbstractProtocolComponent;
import reptor.replct.common.settings.SettingsReader;


public class Execution extends AbstractProtocolComponent
{

    private final WorkDistribution  m_orderdist;
    private final int[]                 m_chkpt_to_exect;

    private int[][]                     m_exect_to_chkpts;
    private WorkDistribution        m_exectdist;


    public Execution(WorkDistribution orderdist, int[] chkpt_to_exect)
    {
        m_orderdist      = Objects.requireNonNull( orderdist );
        m_chkpt_to_exect = Objects.requireNonNull( chkpt_to_exect );
    }


    public Execution load(SettingsReader reader)
    {
        loadBasicSettings( reader, "exect" );

        return this;
    }


    @Override
    public Execution activate()
    {
        super.activate();

        m_exectdist = m_nworkers==1 ? new WorkDistribution.Continuous( 0 ) : m_orderdist;
        m_exect_to_chkpts = reverseIndex( m_chkpt_to_exect, m_nworkers );

        return this;
    }


    public WorkDistribution getOrderInstanceDistribution()
    {
        return m_exectdist;
    }


    public int[] getLinkedCheckpointShardsForExecutor(int exect)
    {
        return m_exect_to_chkpts[ exect ];
    }

}
