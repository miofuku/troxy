package reptor.bench.apply.http;

import reptor.bench.Benchmark;
import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.replct.service.ServiceInstance;

/**
 * Created by bli on 3/22/17.
 */
public class HttpBenchmark extends Benchmark {

    @Override
    public ServiceInstance createServiceInstance(int instno, short partno) {
        return new HttpServer();
    }

    @Override
    public CommandGenerator createCommandGenerator(CommandResultProcessor<? super BenchmarkCommand> resproc) {
        return new HttpClient();
    }
}
