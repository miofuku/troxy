package reptor.bench;

import com.google.common.base.Preconditions;

import reptor.replct.common.settings.SettingsReader;
import reptor.replct.service.ServiceInstance;


public abstract class Benchmark
{

    protected short         m_nclients          = 1;
    protected int           m_openreqsperclient = 1;

    protected boolean       m_isactive = false;


    public Benchmark load(SettingsReader reader)
    {
        Preconditions.checkState( !m_isactive );

        m_nclients          = reader.getShort( "clients.number", m_nclients );
        m_openreqsperclient = reader.getInt( "benchmark.requests_per_client", m_openreqsperclient );

        return this;
    }


    public Benchmark activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    public abstract ServiceInstance createServiceInstance(int instno, short partno);


    public abstract CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc);


    public short getNumberOfClients()
    {
        return m_nclients;
    }


    public int getNumberOfOpenRequestsPerClient()
    {
        return m_openreqsperclient;
    }

}
