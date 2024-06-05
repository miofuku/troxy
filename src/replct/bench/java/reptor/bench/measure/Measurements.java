package reptor.bench.measure;

import com.google.common.base.Preconditions;

import reptor.replct.common.settings.SettingsReader;


public class Measurements
{

    private boolean     m_clitrans      = false;
    private boolean     m_reptrans      = false;
    private boolean     m_execdreqs     = false;
    private boolean     m_appldchkpts   = false;
    private boolean     m_procdreqs     = false;
    private boolean     m_consinsts     = false;

    private boolean     m_isactive = false;


    public Measurements load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        m_clitrans    = reader.getBool( "benchmark.client.measure_transmitted_data", m_clitrans );
        m_reptrans    = reader.getBool( "benchmark.replica.measure_transmitted_data", m_reptrans );
        m_execdreqs   = reader.getBool( "benchmark.replica.measure_executed_requests", m_execdreqs );
        m_appldchkpts = reader.getBool( "benchmark.replica.measure_applied_checkpoints", m_appldchkpts );
        m_procdreqs   = reader.getBool( "benchmark.replica.measure_processed_requests", m_procdreqs );
        m_consinsts   = reader.getBool( "benchmark.replica.measure_consensus_instances", m_consinsts );

        return this;
    }


    public Measurements activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public boolean getMeasureClientTransmission()
    {
        return m_clitrans;
    }


    public boolean getMeasureReplicaTransmission()
    {
        return m_reptrans;
    }


    public boolean getMeasureExecutedRequests()
    {
        return m_execdreqs;
    }


    public boolean getMeasureAppliedCheckpoints()
    {
        return m_appldchkpts;
    }


    public boolean getMeasureProcessedRequests()
    {
        return m_procdreqs;
    }


    public boolean getMeasureConsensusInstances()
    {
        return m_consinsts;
    }

}
