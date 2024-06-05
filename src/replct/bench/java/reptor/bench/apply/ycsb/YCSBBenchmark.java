package reptor.bench.apply.ycsb;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.replct.service.ServiceInstance;

/**
 * Created by bli on 5/8/17.
 */
public class YCSBBenchmark extends Benchmark {

    private final boolean load;

    public YCSBBenchmark(boolean load) {
        this.load = load;
    }

    @Override
    public ServiceInstance createServiceInstance(int instno, short partno) {
        return new YCSBServer(load);
    }

    @Override
    public CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc) {
        return null;
    }
}
