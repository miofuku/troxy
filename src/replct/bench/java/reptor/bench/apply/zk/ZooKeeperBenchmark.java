package reptor.bench.apply.zk;

import com.google.common.base.Preconditions;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.service.ServiceInstance;


public class ZooKeeperBenchmark extends Benchmark
{

    private int     m_datasizemin   = 0;
    private int     m_datasizemax   = 2048;
    private int     m_nnodes        = 1000;
    private int     m_writerate     = 50;
    private int     m_lossrate      = 0;


    @Override
    public ZooKeeperBenchmark load(SettingsReader reader)
    {
        super.load( reader );

        SettingsReader config = new SettingsReader( System.getProperties() );

        m_datasizemin = config.getInt( "zk.dsmin", m_datasizemin );
        m_datasizemax = config.getInt( "zk.dsmax", m_datasizemax );
        m_nnodes      = config.getInt( "zk.nodes", m_nnodes );
        m_writerate   = config.getInt( "zk.writerate", m_writerate );
        m_lossrate    = config.getInt( "zk.lossrate", m_lossrate );

        return this;
    }


    @Override
    public ServiceInstance createServiceInstance(int instno, short partno)
    {
        Preconditions.checkState( m_isactive );

        return new ZooKeeperServer();
    }


    @Override
    public CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc)
    {
        Preconditions.checkState( m_isactive );

        return new ZooKeeperClient( resproc, m_datasizemin, m_datasizemax, m_nnodes, m_writerate, m_lossrate );
    }


    public int getMinimumDataSize()
    {
        return m_datasizemin;
    }


    public int getMaximumDataSize()
    {
        return m_datasizemax;
    }


    public int getNumberOfNodes()
    {
        return m_nnodes;
    }


    public int getWriteRate()
    {
        return m_writerate;
    }


    public int getLossRate()
    {
        return m_lossrate;
    }

}
