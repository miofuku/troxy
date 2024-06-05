package reptor.bench.apply.zero;

import com.google.common.base.Preconditions;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.service.ServiceInstance;


public class ZeroBenchmark extends Benchmark
{

    private int     m_reqsize       = 0;
    private int     m_replysize     = 0;
    private int     m_chkptsize     = 0;
    private int     m_writerate     = 100;
    private int     m_conflictrate  = 0;
    private boolean m_prophecy      = false;


    @Override
    public ZeroBenchmark load(SettingsReader reader)
    {
        super.load( reader );

        m_reqsize      = reader.getInt( "benchmark.request_size", m_reqsize );
        m_replysize    = reader.getInt( "benchmark.reply_size", m_replysize );
        m_chkptsize    = reader.getInt( "benchmark.state_size", m_chkptsize );
        m_writerate    = reader.getInt( "benchmark.zero.writerate", m_writerate );
        m_conflictrate = reader.getInt( "benchmark.zero.conflictrate", m_conflictrate );
        m_prophecy     = reader.getBool( "benchmark.zero.prophecy", m_prophecy );

        return this;
    }


    @Override
    public ServiceInstance createServiceInstance(int instno, short partno)
    {
        Preconditions.checkState( m_isactive );

        return new ZeroServer( instno, m_replysize, m_chkptsize, m_conflictrate );
    }


    @Override
    public CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc)
    {
        Preconditions.checkState( m_isactive );

        if (m_prophecy)
            return new ProphecyClient( resproc, m_writerate );
        else
            return new ZeroClient( resproc, m_reqsize, m_writerate );
    }


    public int getRequestSize()
    {
        return m_reqsize;
    }


    public int getReplySize()
    {
        return m_replysize;
    }


    public int getCheckpointSize()
    {
        return m_chkptsize;
    }


    public int getWriteRate()
    {
        return m_writerate;
    }


    public int getConflictRate()
    {
        return m_conflictrate;
    }


    public boolean getProphecy() { return m_prophecy; }

}
