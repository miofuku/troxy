#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from datetime import datetime
import platform
import subprocess

from plib.shell import GenericShellCommand
from plib.utils import Value
from reptor.system import ProcessGroupStartMode, Reptor


def ThreeF(f):
    return 3*f + 1

def TwoF(f):
    return 2*f + 1

class BFTProtocol:
    def __init__(self, name, mode, f_to_n, configname, ishybrid):
        self.name       = name
        self.mode       = mode
        self.nreplicas  = f_to_n
        self.configname = configname
        self.ishybrid   = ishybrid

    def __str__(self):
        return self.name

    HybsterX   = None
    HybsterS   = None
    PBFT       = None
    HybridPBFT = None

BFTProtocol.HybsterX   = BFTProtocol( 'HybsterX', 'COP', TwoF, 'hybsterx', True )
BFTProtocol.HybsterS   = BFTProtocol( 'HybsterS', 'SOTA', TwoF, 'hybsterx', True )
BFTProtocol.PBFT       = BFTProtocol( 'PBFT', 'COP', ThreeF, 'pbftx', False )
BFTProtocol.HybridPBFT = BFTProtocol( 'HybridPBFT', 'COP', ThreeF, 'pbftx', True )


# TODO: Cf. Network environment.
#       Add model for CPU Topologies (CPU, CPUCore, CPUThread) and/or name scheme. Supports SGX and other features.
#       Number of machines for process types.
class Platform:
    def __init__(self, name, clidomcfg, cores_to_affinity, nclienthosts, trinx_library):
        self.name = name
        self.client_domain_config = clidomcfg
        self.cores_to_affinity    = cores_to_affinity
        self.nclienthosts         = nclienthosts
        self.trinx_library         = trinx_library

    @property
    def number_of_cores(self):
        return len( self.cores_to_affinity )-1

    def __str__(self):
        return self.name

    Beagles = None
    SGXs    = None
    Local   = None

Platform.Beagles = Platform( 'beagles', '24',
                             ('0', '0,12', '0-1,12-13', None, '0-3,12-15', None, '0-5,12-17',
                                   None  , '0-7,12-19', None, '0-9,12-21', None, '0-11,12-23'),
                             4, 'libtrinx-jni-ossl.{ext}' )
Platform.SGXs    = Platform( 'sgxs', '8', ('0', '0,4', '0-1,4-5', '0-2,4-6', '0-3,4-7'), 2, 'libtrinx-jni-sgx-s.{ext}' )
Platform.Local   = Platform( 'local', '4', ('', '', '', '', ''), 1, 'libtrinx-jni-ossl.{ext}' )


class Benchmarker:
    ncores_to_domain_config = \
        {
        'SOTA': ('COP1I4', '4O1C2I4', '4O1C2I4' , '4O1C2I4', '4O1C2I4', None, '4O1C2I4',
                            None     , '4O1C2I4' , None     , '4O1C2I4', None, '4O1C2I4'),

        'COP':  ('COP1I4', 'COP2I4' , 'COP4EI4' , 'COP6EI4', 'COP8EI4' , None, 'COP12EI4',
                            None     , 'COP16EI4', None     , 'COP20EI4', None, 'COP24EI4')
        }

    platforms_by_name = { 'beagles': Platform.Beagles, 'sgxs': Platform.SGXs, 'local': Platform.Local }

    payload_to_load = \
        {
        0:    ((24, 1, 1), (24, 5, 5), (24, 10, 10), (24, 20, 20), (24, 50, 50), (24, 100, 100), (24, 200, 150), (24, 500, 200), (24, 1000, 200), (24, 2000, 200), (24, 5000, 200)),
        128:  ((24, 1, 1), (24, 5, 5), (24, 10, 10), (24, 20, 20), (24, 50, 50), (24, 100, 100), (24, 200, 150), (24, 500, 200), (24, 1000, 200)),
        512:  ((24, 1, 1), (24, 5, 5), (24, 10, 10), (24, 20, 20), (24, 50, 20), (24, 100, 50), (24, 200, 50), (24, 500, 40)),
        1024: ((24, 1, 1), (24, 5, 5), (24, 10, 10), (24, 20, 15), (24, 50, 15), (24, 100, 15), (24, 200, 15)),
        4096: ((24, 1, 1), (24, 6, 3), (24, 10, 5), (24, 20, 5), (24, 50, 5))
        }

    hybrid_to_crypto = \
        {
        False:  (('SHA256', 'HMAC_SHA256', 'default', 'HMAC_SHA256'),),
        True:   (('SHA256', 'HMAC_SHA256', 'default', 'TMAC_HMAC_SHA256'),)
        }

    protocols = BFTProtocol.HybsterX, BFTProtocol.HybsterS, BFTProtocol.HybridPBFT, BFTProtocol.PBFT

    def __init__(self, execenv, platform):
        self.environment = execenv
        self.platform    = self.platforms_by_name[ platform ]

        self.config = BenchmarkConfiguration()
        self.config.platform     = self.platform
        self.config.nclienthosts = self.platform.nclienthosts

    def execute(self, dryrun=False):
        cntxt = BenchmarkContext( self.config )

        runbench = ExecuteDryRun() if dryrun else ExecuteRun( self.environment )

        scalability  = self._scalability_benchmark( 'scalability', False, runbench )
        limit        = self._scalability_benchmark( 'limit', True, runbench )
        latency      = self._latency_benchmark( runbench )
        crypto       = self._crypto_benchmark( 'crypto', False, runbench )
        crypto_limit = self._crypto_benchmark( 'crypto_limit', True, runbench )
        zk           = self._zk_benchmark( runbench )

        total = ChainedBenchmark( 'total' )
        total.benchmarks = [bench for bench in (scalability, limit, latency, crypto, crypto_limit, zk) if bench]
        total.execute( cntxt )

    def _scalability_benchmark(self, name, limit, innerbench):
        benchmark = NestedBenchmark( name )

        benchmark.add( SetBenchmarkName( name ) )
        benchmark.add( BenchmarkBatches( (1, 200) ) )
        benchmark.add( BenchmarkProtocols( self.protocols ) )
        benchmark.add( BenchmarkCrypto( self.hybrid_to_crypto ) )
        if limit: benchmark.add( BenchmarkLimit() )
        benchmark.add( BenchmarkCores( self.ncores_to_domain_config, self.platform ))
        benchmark.add( innerbench )

        return benchmark

    def _latency_benchmark(self, innerbench):
        benchmark = NestedBenchmark( 'latency' )

        benchmark.add( SetBenchmarkName( 'latency' ) )
        benchmark.add( BenchmarkBatches( (200,) ) )
        benchmark.add( BenchmarkProtocols( self.protocols ) )
        benchmark.add( BenchmarkCrypto( self.hybrid_to_crypto ) )
        benchmark.add( BenchmarkCores( self.ncores_to_domain_config, self.platform, (self.platform.number_of_cores,) ))
        benchmark.add( BenchmarkPayloads( (0, 128, 1024, 4096) ) )
        benchmark.add( BenchmarkLoads( self.payload_to_load ) )
        benchmark.add( innerbench )

        return benchmark

    def _crypto_benchmark(self, name, limit, innerbench):
        crypto_settings = \
            {
            False:  (
                        ('SHA256', 'HMAC_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'DSA_1024_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'RSA_1024_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'ECDSA_192_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'TMAC_HMAC_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'DSA_1024_SHA256', 'HMAC_SHA256', 'DSA_1024_SHA256'),
                        ('SHA256', 'RSA_1024_SHA256', 'HMAC_SHA256', 'RSA_1024_SHA256'),
                        ('SHA256', 'ECDSA_192_SHA256', 'HMAC_SHA256', 'ECDSA_192_SHA256'),
                        ('SHA256', 'DSA_1024_SHA256', 'DSA_1024_SHA256', 'DSA_1024_SHA256'),
                        ('SHA256', 'RSA_1024_SHA256', 'RSA_1024_SHA256', 'RSA_1024_SHA256'),
                        ('SHA256', 'ECDSA_192_SHA256', 'ECDSA_192_SHA256', 'ECDSA_192_SHA256'),
                        ('SHA256', 'HMAC_SHA256', 'HMAC_SHA256', 'DSA_1024_SHA256'),
                        ('SHA256', 'HMAC_SHA256', 'HMAC_SHA256', 'RSA_1024_SHA256'),
                        ('SHA256', 'HMAC_SHA256', 'HMAC_SHA256', 'ECDSA_192_SHA256'),
                        ('MD5', 'MD5', 'MD5', 'MD5')
                    ),
            True:   (
                        ('SHA256', 'HMAC_SHA256', 'HMAC_SHA256', 'HMAC_SHA256'),
                        ('SHA256', 'DSA_1024_SHA256', 'HMAC_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'RSA_1024_SHA256', 'HMAC_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'ECDSA_192_SHA256', 'HMAC_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'TMAC_HMAC_SHA256', 'HMAC_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'DSA_1024_SHA256', 'DSA_1024_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'RSA_1024_SHA256', 'RSA_1024_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'ECDSA_192_SHA256', 'ECDSA_192_SHA256', 'TMAC_HMAC_SHA256'),
                        ('SHA256', 'TMAC_HMAC_SHA256', 'TMAC_HMAC_SHA256', 'TMAC_HMAC_SHA256')
                    )
            }

        benchmark = NestedBenchmark( name )

        benchmark.add( SetBenchmarkName( name ) )
        benchmark.add( BenchmarkBatches( (1, 200) ) )
        benchmark.add( BenchmarkProtocols( self.protocols ) )
        benchmark.add( BenchmarkCrypto( crypto_settings ) )
        if limit: benchmark.add( BenchmarkLimit() )
        benchmark.add( BenchmarkCores( self.ncores_to_domain_config, self.platform, (self.platform.number_of_cores,) ))
        benchmark.add( innerbench )

        return benchmark

    def _zk_benchmark(self, innerbench):
        benchmark = NestedBenchmark( 'zk' )

        benchmark.add( SetBenchmarkName( 'zk' ) )
        benchmark.add( BenchmarkBatches( (200,) ) )
        benchmark.add( BenchmarkProtocols( self.protocols ) )
        benchmark.add( BenchmarkCrypto( self.hybrid_to_crypto ) )
        benchmark.add( BenchmarkCores( self.ncores_to_domain_config, self.platform, (self.platform.number_of_cores,) ))
        benchmark.add( BenchmarkZooKeeper() )
        benchmark.add( innerbench )

        return benchmark


class Benchmark(metaclass=ABCMeta):
    @abstractmethod
    def execute(self, cntxt):
        ...

    def execute_subbenchmark(self, cntxt):
        raise NotImplementedError()


class CompoundBenchmark(Benchmark):
    def __init__(self, name):
        self.name       = name
        self.benchmarks = []

    def add(self, benchmark):
        self.benchmarks.append( benchmark )
        return self

# TODO: Iterative version instead of recursive?
class NestedBenchmark(CompoundBenchmark):
    def __init__(self, name):
        super().__init__( name )
        self.current_benchno = -1

    def execute(self, cntxt):
        cntxt.enter_benchmark( self )
        cntxt.execute_series( self.name )
        cntxt.leave_benchmark()

    def execute_subbenchmark(self, cntxt):
        self.current_benchno += 1
        self.benchmarks[ self.current_benchno ].execute( cntxt )
        self.current_benchno -= 1


class ChainedBenchmark(CompoundBenchmark):
    def execute(self, cntxt):
        cntxt.enter_benchmark( self )
        cntxt.start_series( self.name )

        for benchmark in self.benchmarks:
            benchmark.execute( cntxt )

        cntxt.series_finished()
        cntxt.leave_benchmark()


class BenchmarkContext:
    def __init__(self, config):
        self.config = config

        self.bench_stack = []
        self.run_stack   = []

    def enter_benchmark(self, benchmark):
        self.bench_stack.append( benchmark )

    def execute_subbenchmark(self):
        self.bench_stack[ -1 ].execute_subbenchmark( self )

    def leave_benchmark(self):
        self.bench_stack.pop()

    def start_run(self):
        run = self._start_run( 'run' )
        run.run_started()

        print( self._run_start_message( run ) )

        return run

    def run_finished(self):
        return self._run_finished()

    def execute_series(self, name):
        self.start_series( name )
        self.execute_subbenchmark()
        self.series_finished()

    def start_series(self, name):
        return self._start_run( name )

    def series_finished(self):
        run = self._run_finished()

        print( self._run_end_message( run ) )

        return run

    def _start_run(self, name):
        run = self.run_stack[ -1 ].subseries( name ) if self.run_stack else BenchmarkRun( name )
        self.run_stack.append( run )
        return run

    def _run_finished(self):
        run = self.run_stack.pop()
        run.finished()
        return run

    def _run_start_message(self, run):
        return '{run.start_time:%H:%M:%S} --> start run {run.number}: {config}' \
                    .format( run=run, config=self.config.run_message() )

    def _run_end_message(self, run):
        totdur = run.end_time-self.run_stack[ 0 ].start_time if self.run_stack else run.duration

        return '{run.end_time:%H:%M:%S} --> finished {run.name} in {run.duration} (runs: {run.count}; total {run.total_count} in {total_duration})\n' \
                    .format( run=run, total_duration=totdur )


class BenchmarkRun:
    def __init__(self, name,  parent=None):
        self.name          = name
        self.parent        = parent
        self.number        = parent.current_runno if parent else 1
        self.start_time    = self._time()
        self.end_time      = None

        self.start_runno   = self.number
        self.current_runno = self.number

    def subseries(self, name):
        return BenchmarkRun( name, self )

    def run_started(self):
        self.current_runno += 1

        if self.parent:
            self.parent.run_started()

    def finished(self):
        self.end_time = self._time()

    @property
    def duration(self):
        return self.end_time-self.start_time

    @property
    def count(self):
        return self.current_runno-self.start_runno

    @property
    def total_count(self):
        return self.current_runno-1

    def _time(self):
        return datetime.now().replace( microsecond=0 )


# TODO: Introduce independent parameters or benchmark steps that alter the system configuration
#       and there should be only a single configuration, that is, the system itself.
#       The problem: There is no possibility to revert changes made by configuration files.
#       As a consequence, for each run a new system instance is currently needed.
class BenchmarkConfiguration:
    def __init__(self):
        self.protocol               = BFTProtocol.HybsterX

        self.benchmark_name            = Value.Undefined
        self.clients_cert_algo         = Value.Undefined
        self.replies_cert_algo         = Value.Undefined
        self.replicas_strong_cert_algo = Value.Undefined
        self.trinx_library           = Value.Undefined
        self.trinx_enclave           = Value.Undefined
        self.message_digest         = Value.Undefined
        self.tss                    = Value.Undefined

        self.batchsize_min          = 200
        self.batchsize_max          = 200
        self.repliers               = Value.Undefined
        self.rotate                 = 'false'
        self.checkpoint_mode        = Value.Undefined
        self.replica_domain_config  = Value.Undefined
        self.client_domain_config   = Value.Undefined

        self.servicename            = Value.Undefined
        self.zk_nnodes_client       = Value.Undefined
        self.zk_writerate           = Value.Undefined
        self.zk_nnodes              = Value.Undefined
        self.zk_dsmin               = Value.Undefined
        self.zk_dsmax               = Value.Undefined
        self.zk_hashblock           = Value.Undefined

        self.platform               = Value.Undefined
        self.ncores                 = Value.Undefined
        self.process_affinity       = Value.Undefined
        self.nclienthosts           = 1

        self.duration               = 120
        self.nclients               = 192
        self.reqs_per_client        = 2000
        self.request_size           = Value.Undefined
        self.reply_size             = Value.Undefined

    @property
    def total_reqs(self):
        return self.nclients*self.reqs_per_client

    def __call__(self, execenv, system):
        execenv.load_base_config()

        execenv.set_protocol_config_by_name( self.protocol.configname )
        execenv.load_protocol_config()

        if self.replica_domain_config!=Value.Undefined:
            execenv.set_replica_scheduler_config_by_name( self.replica_domain_config )
        if self.client_domain_config!=Value.Undefined:
            execenv.set_client_scheduler_config_by_name( self.client_domain_config )
        execenv.load_scheduler_config()

        system.clients().set_count( self.nclienthosts )
        system.monitors().monitor_per_thread = True

        if self.process_affinity!=Value.Undefined:
            system.benchmark_settings.process_affinity = self.process_affinity
        if self.ncores!=Value.Undefined:
            system.benchmark_settings.ncores = self.ncores

        if self.servicename!=Value.Undefined:
            system.benchmark_settings.servicename = self.servicename

        system.benchmark_settings.protocol_variant    = self.protocol.name
        system.benchmark_settings.benchmark_name      = self.benchmark_name
        system.benchmark_settings.duration            = self.duration
        system.benchmark_settings.client_count        = self.nclients
        system.benchmark_settings.requests_per_client = self.reqs_per_client
        system.benchmark_settings.request_size        = self.request_size
        system.benchmark_settings.reply_size          = self.reply_size
        system.global_settings.batchsize_min          = self.batchsize_min
        system.global_settings.batchsize_max          = self.batchsize_max
        system.global_settings.repliers               = self.repliers
        system.global_settings.rotate                 = self.rotate
        system.global_settings.checkpoint_mode        = self.checkpoint_mode

        system.benchmark_settings.zk_nnodes_client    = self.zk_nnodes_client
        system.benchmark_settings.zk_writerate        = self.zk_writerate
        system.benchmark_settings.zk_nnodes           = self.zk_nnodes
        system.benchmark_settings.zk_dsmin            = self.zk_dsmin
        system.benchmark_settings.zk_dsmax            = self.zk_dsmax
        system.benchmark_settings.zk_hashblock        = self.zk_hashblock

        def set_global(name):
            value = getattr( self, name )
            if value!=Value.Undefined:
                setattr( system.global_settings, name, value )

        set_global( 'clients_cert_algo' )
        set_global( 'replies_cert_algo' )
        set_global( 'replicas_strong_cert_algo' )
        set_global( 'message_digest' )

        if self.protocol.ishybrid:
            system.global_settings.replicas_trusted          = self.tss if self.tss!=Value.Undefined else 'trinx'
            system.global_settings.replicas_strong_cert_algo = 'TMAC_HMAC_SHA256'

            libext = 'dynlib' if platform.system()=='Darwin' else 'so'

            trinxlib = self.trinx_library if self.trinx_library!=Value.Undefined else self.platform.trinx_library
            system.global_settings.trinx_library = execenv.trinxdir / 'lib' / trinxlib.format( ext=libext )
            system.global_settings.trinx_enclave = execenv.trinxdir / 'lib' / 'libtrinx-opt-b.signed.{ext}'.format( ext=libext )


    def run_message(self):
        return '{config.protocol.name} ({config.replica_domain_config}) with ' \
               '{config.ncores} cores ({config.process_affinity}) ' \
               'clients {config.nclients} (x{config.reqs_per_client}={config.total_reqs} reqs)' \
                    .format( config=self )

    def run_name(self):
        parts = []
        parts.append( self.platform.name )

        if self.request_size!=Value.Undefined:
            parts.append( 'P' + str( self.request_size ) )

        if self.batchsize_min!=Value.Undefined:
            parts.append( 'B' + str( self.batchsize_min ) )

        if self.ncores!=Value.Undefined:
            parts.append( str( self.ncores ) + 'C' )

        return '-'.join( parts )

    def run_desc(self):
        return self.platform.name


class SetBenchmarkName(Benchmark):
    def __init__(self, name):
        self.name = name

    def execute(self, cntxt):
        save = cntxt.config.benchmark_name

        cntxt.config.benchmark_name = self.name
        cntxt.execute_subbenchmark()

        cntxt.config.benchmark_name = save


class BenchmarkCrypto(Benchmark):
    def __init__(self, hybrid_to_crypto):
        self.hybrid_to_crypto = hybrid_to_crypto

    def execute(self, cntxt):
        save = cntxt.config.message_digest, cntxt.config.clients_cert_algo, cntxt.config.replies_cert_algo, \
               cntxt.config.replicas_strong_cert_algo

        crypto_list = self.hybrid_to_crypto[ cntxt.config.protocol.ishybrid ]

        for crypto in crypto_list:
            cntxt.config.message_digest, cntxt.config.clients_cert_algo, cntxt.config.replies_cert_algo, \
                    cntxt.config.replicas_strong_cert_algo = crypto

            if len( crypto_list )>1:
                cntxt.execute_series( 'crypto {}/{}/{}/{}'.format( *crypto ) )
            else:
                cntxt.execute_subbenchmark()

        cntxt.config.message_digest, cntxt.config.clients_cert_algo, cntxt.config.replies_cert_algo, \
                cntxt.config.replicas_strong_cert_algo = save


class BenchmarkBatches(Benchmark):
    def __init__(self, batchsizes):
        self.batchsizes = batchsizes

    def execute(self, cntxt):
        save = cntxt.config.batchsize_min, cntxt.config.batchsize_max

        for batchsize in self.batchsizes:
            if isinstance( batchsize, int):
                cntxt.config.batchsize_min = cntxt.config.batchsize_max = batchsize
            else:
                cntxt.config.batchsize_min, cntxt.config.batchsize_max = batchsize

            if len( self.batchsizes ):
                cntxt.execute_series( 'batchsize {}-{}'.format( cntxt.config.batchsize_min, cntxt.config.batchsize_max ) )
            else:
                cntxt.execute_subbenchmark()

        cntxt.config.batchsize_min, cntxt.config.batchsize_max = save


class BenchmarkProtocols(Benchmark):
    def __init__(self, protocols):
        self.protocols = protocols

    def execute(self, cntxt):
        save = cntxt.config.protocol, cntxt.config.repliers

        for protocol in self.protocols:
            cntxt.config.protocol = protocol
            cntxt.config.repliers = protocol.nreplicas( 1 )

            if len( self.protocols )>1:
                cntxt.execute_series( 'protocol {}'.format( protocol.name ) )
            else:
                cntxt.execute_subbenchmark()

        cntxt.config.protocol, cntxt.config.repliers = save


class BenchmarkCores(Benchmark):
    def __init__(self, ncores_to_domain_config, platform, ncoreslist=None):
        self.ncores_to_domain_config = ncores_to_domain_config
        self.ncoreslist = ncoreslist
        self.platform   = platform

    def execute(self, cntxt):
        save = cntxt.config.process_affinity, cntxt.config.ncores, cntxt.config.replica_domain_config, cntxt.config.client_domain_config

        if self.ncoreslist is None:
            ncoreslist = range( len( self.ncores_to_domain_config[ cntxt.config.protocol.mode ] ) )
        else:
            ncoreslist = self.ncoreslist

        for ncores in ncoreslist:
            if ncores>=len( self.platform.cores_to_affinity ) or ncores>=len( self.ncores_to_domain_config[ cntxt.config.protocol.mode ] ):
                break

            repdomcfg = self.ncores_to_domain_config[ cntxt.config.protocol.mode ][ ncores ]

            if not repdomcfg:
                continue

            affinity = self.platform.cores_to_affinity[ ncores ]

            if affinity is None:
                continue
            elif affinity:
                cntxt.config.process_affinity = affinity

            cntxt.config.ncores                = ncores
            cntxt.config.replica_domain_config = repdomcfg
            cntxt.config.client_domain_config  = self.platform.client_domain_config

            if len( ncoreslist )>1:
                cntxt.execute_series( 'cores {}'.format( ncores ) )
            else:
                cntxt.execute_subbenchmark()

        cntxt.config.process_affinity, cntxt.config.ncores, cntxt.config.replica_domain_config, cntxt.config.client_domain_config = save


class BenchmarkLimit(Benchmark):
    def execute(self, cntxt):
        save = cntxt.config.repliers, cntxt.config.rotate

        cntxt.config.repliers = 'woleader'
        cntxt.config.rotate   = 'block'
        cntxt.execute_subbenchmark()

        cntxt.config.repliers, cntxt.config.rotate = save


class BenchmarkPayloads(Benchmark):
    def __init__(self, payloadsizes):
        self.payloadsizes = payloadsizes

    def execute(self, cntxt):
        save = cntxt.config.request_size, cntxt.config.reply_size

        for payloadsize in self.payloadsizes:
            if isinstance( payloadsize, int):
                reqsize = repsize = payloadsize
            else:
                reqsize, repsize = payloadsize

            cntxt.config.request_size = reqsize
            cntxt.config.reply_size   = repsize

            if len( self.payloadsizes )>1:
                cntxt.execute_series( 'payloads {}/{}'.format( reqsize, repsize ) )
            else:
                cntxt.execute_subbenchmark()

        cntxt.config.request_size, cntxt.config.reply_size = save


class BenchmarkLoads(Benchmark):
    def __init__(self, payload_to_load):
        self.payload_to_load = payload_to_load

    def execute(self, cntxt):
        save = cntxt.config.nclients, cntxt.config.reqs_per_client, cntxt.config.batchsize_min

        for nclients, reqs_per_client, batchsize_min in self.payload_to_load[ cntxt.config.request_size ]:
            cntxt.config.nclients        = nclients
            cntxt.config.reqs_per_client = reqs_per_client
            cntxt.config.batchsize_min   = batchsize_min

            cntxt.execute_series( 'loads {}x{} - {}'.format( nclients, reqs_per_client, batchsize_min ) )

        cntxt.config.nclients, cntxt.config.reqs_per_client, cntxt.config.batchsize_min = save


class BenchmarkZooKeeper(Benchmark):
    def execute(self, cntxt):
        save = cntxt.config.checkpoint_mode, cntxt.config.servicename, \
               cntxt.config.zk_hashblock, cntxt.config.zk_nnodes, cntxt.config.zk_dsmin, \
               cntxt.config.zk_dsmax, cntxt.config.zk_writerate

        cntxt.config.checkpoint_mode = 'send'
        cntxt.config.servicename     = 'zk'
        cntxt.config.zk_hashblock    = 0
        cntxt.config.zk_nnodes       = 10000
        cntxt.config.zk_dsmin        = 128
        cntxt.config.zk_dsmax        = 128

        for writerate in 0, 25, 50, 75, 100:
            cntxt.config.zk_writerate = writerate

            cntxt.execute_series( 'zk {}'.format( writerate ) )

        cntxt.config.checkpoint_mode, cntxt.config.servicename, \
               cntxt.config.zk_hashblock, cntxt.config.zk_nnodes, cntxt.config.zk_dsmin, \
               cntxt.config.zk_dsmax, cntxt.config.zk_writerate = save


class ExecuteRun(Benchmark):
    def __init__(self, execenv):
        self.environment = execenv

    def execute(self, cntxt):
        cntxt.start_run()

        system = Reptor()
        self.environment.set_system( system )

        cntxt.config( self.environment, system )

        self.environment.setup_run()

        try:
            system.start_replicas( mode=ProcessGroupStartMode.panes )
            system.start_monitors()
            system.start_clients( mode=ProcessGroupStartMode.panes )

            system.start_benchmark()

            system.await_benchmark_finished()
        except subprocess.TimeoutExpired as e:
            print( 'Error: {}'.format( e ) )

        self._kill_all_processes( system )
        system.stop_processes()

        if system.current_run.starttime is None:
            system.current_run.starttime = datetime.now()

        system.collect_benchmarkdata()
        system.save_benchmarkresults( cntxt.config.run_name(), cntxt.config.run_desc() )

        cntxt.run_finished()

    # TODO: Should be part of the system or the environment or whatever.
    def _kill_all_processes(self, system):
        hosts = self.environment.netenv.all_concrete_hosts( system )

        remcmd = GenericShellCommand( self.environment.controlscript, 'kill', '--local' )

        for host in hosts:
            if host.is_local:
                system.kill_local_processes()
            else:
                print( 'Connect to {}'.format( host ) )
                system.create_sshcmd( self.environment, host ).command( remcmd ).execute()


class ExecuteDryRun(Benchmark):
    def execute(self, cntxt):
        cntxt.start_run()

        for key, value in sorted( cntxt.config.__dict__.items() ):
            print( '{:40} {}'.format( key, value ) )

        cntxt.run_finished()
