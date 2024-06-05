package reptor.bench.apply.counter;

import com.google.common.base.Preconditions;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.service.ServiceInstance;


public class CounterBenchmark extends Benchmark
{

    private final int   m_clinobase;
    private int         m_reqsize   = 0;
    private int         m_replysize = 0;


    public CounterBenchmark(int clinobase)
    {
        m_clinobase = clinobase;
    }


    @Override
    public CounterBenchmark load(SettingsReader reader)
    {
        super.load( reader );

        m_reqsize   = reader.getInt( "benchmark.request_size", m_reqsize );
        m_replysize = reader.getInt( "benchmark.reply_size", m_replysize );

        return this;
    }


    @Override
    public ServiceInstance createServiceInstance(int instno, short partno)
    {
        Preconditions.checkState( m_isactive );

        return new CounterServer( 0, 1, m_clinobase, m_nclients, m_replysize );
    }


    @Override
    public CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc)
    {
        Preconditions.checkState( m_isactive );

        return new CounterClient( resproc, m_reqsize );
    }

}
