#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from collections import OrderedDict
import datetime
from enum import Enum
from operator import methodcaller
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time

from exprmt.ds import ServiceNetwork, DistributedSystem, ControlNetwork, SystemProcess, SystemNetworkGroup, \
    SystemHost, SystemEndpoint, ServiceProcess, ReplicaGroup, BenchmarkClientGroup, Replica, BenchmarkClient
from exprmt.system import SystemEntityGroup
from plib.shell import Timeout, StdErrToStdOut, ToFile, GenericShellCommand
from plib.shell.files import DeleteFile, CreateDir, ChangeDir
from plib.shell.io import Tee
from plib.shell.java import Java
from plib.shell.ssh import RemoteIdentity, RemoteLogin, SSH, SCP, RemotePath
from plib.shell.tmux import Tmux
from plib.utils import Value
import psutil
from reptor.config import ReptorSystemConfigMapping
from reptor.procs import start_procs_in_background, start_procs_in_tmux_windows, start_procs_in_tmux_panes, \
    JavaProcess, ShellProcess
from reptor.shell import NUMACtl
from translt.fields import DateTimeField
from translt.fieldsets import RecordFieldSet
from translt.files import FileMapping


class BenchmarkRun:
    def __init__(self, isnew=False):
        self.starttime        = None
        self.maximum_duration = None
        self.endtime          = None

        # TODO: Persistence...
        self._changed = isnew

    def start(self, maxdur):
        assert not self.starttime
        self.starttime = datetime.datetime.now( datetime.timezone.utc ).astimezone()
        self.maximum_duration = maxdur
        self._changed  = True

    def finish(self):
        assert self.starttime and not self.endtime
        self.endtime  = datetime.datetime.now( datetime.timezone.utc ).astimezone()
        self._changed = True

    def hibernate(self, file):
        if self._changed:
            self._benchrun_mapping().write_object_to( file, self )
            self._changed = False

    @classmethod
    def load_benchmark_run(cls, file):
        return cls._benchrun_mapping().read_object_from( file )

    _benchrunmap = None

    @classmethod
    def _benchrun_mapping(cls):
        if cls._benchrunmap is None:
            cls._benchrunmap = FileMapping.keyvalue( cls._create_benchrun_field() )
        return cls._benchrunmap

    @classmethod
    def _create_benchrun_field(cls):
        return RecordFieldSet().create_composite( cls ) \
                                .init_field( DateTimeField, 'starttime' ).attribute().end() \
                                .start_float( 'maximum_duration' ).attribute().end() \
                                .init_field( DateTimeField, 'endtime' ).attribute().end()


# TODO: The system should not be its own CLI. It should provide a mechanism that allows any kind of UI to
#       follow events. Either by subscription or, presumably better, by a job/action object or execution
#       context that identifies the current action.
# TODO: What belongs to the model, the distributed system and what to its UI? Or are most UI functions so
#       generic that they are not necessarily related to user interaction but can be used by anybody and anything,
#       that is, are part of the model? Or is it more a question of what is the system and what its controller?
#       Since the whole purpose of exprmt is to control experiments, it can turn out that the system is the controller.
#       UI: complex operations with many options (args)/policies, System: simple operations/mechanisms
#       Alternative for UI: commands that encapsulate their arguments!
# TODO: What is relation between the system and its configuration? What is environment, for instance config
#       directories. Plugable extensions (circuit) for functionality like working directories?
#       (comprising: settings, UI/controller, and consl adapter
# TODO: shell.execute( cmd ) to allow logging?
# TODO: Separate server config from client config since the latter belongs to benchmarks(?)


class ReptorClientServiceNetwork(ServiceNetwork):
    pass


class ProcessGroupStartMode(Enum):
    background = 0
    windows    = 1
    panes      = 2


# TODO: Hide internal structure (processes, networks, etc.)? -> Just phases: config, run, cleanup
# TODO: Separate processes from system?
# TODO: proc( JavaConfiguration ); JavaConfiguration.create_java_command( proc )? cmdfac.__call__ for lambdas?
class Reptor(DistributedSystem):
    def __init__(self):
        super().__init__()

        self.global_settings      = ReptorGlobalSettings()
        self.benchmark_settings   = ReptorBenchmarkSettings()
        self.java_settings        = JavaSettings()
        self.generic_settings     = OrderedDict()

        self._ctrlnet  = ControlNetwork( self.entities_of_abstract_type( ServiceProcess ) ).register( self )
        # TODO: ReplicaGroup -> name? start? ReplicatedService(DistributedSystem)?
        self._replicas = ReptorReplicaGroup().register( self )
        self._replicas.set_count( 4 )
        self._repnets  = SystemNetworkGroup( ServiceNetwork, self._replicas ).register( self )
        self._repnets.set_count( 1 )
        self._clieps   = SystemNetworkGroup( ServiceNetwork, self._replicas ).register( self )
        self._clieps.set_count( 0 )
        self._clients  = ReptorBenchmarkClientGroup().register( self )
        self._clients.set_count( 1 )
        self._clinet   = ReptorClientServiceNetwork( self._clients ).register( self )

        self._monitors = ReptorMonitorGroup().register( self )

        self.current_run = None
        self.environment = None
        self._syscfgmap  = None
        self._isfinished = False

    def _init_default_entitytypes(self):
        super()._init_default_entitytypes()

        self.add_entitytype( SystemNetworkGroup, ControlNetwork, ServiceNetwork, ReptorClientServiceNetwork,
                             SystemHost, SystemEndpoint,
                             ReptorReplicaGroup, ReptorBenchmarkClientGroup, self.replicatype, self.clienttype,
                             ReptorMonitorGroup, ReptorMonitorProcess )

    @property
    def _sysconf_mapping(self):
        if self._syscfgmap is None:
            self._syscfgmap = ReptorSystemConfigMapping( self )
        return self._syscfgmap

    @property
    def replicatype(self):
        return ReptorReplica

    @property
    def clienttype(self):
        return ReptorClient
        # return ProphecyClient
        # return YCSBClient
        # return HTTPClient

    @property
    def controlnetwork(self):
        return self._ctrlnet

    def replicas(self):
        return self._replicas

    def replicanetworks(self):
        return self._repnets

    # Dedicated endpoints for client connections
    def clientendpoints(self):
        return self._clieps

    def clients(self):
        return self._clients

    @property
    def clientnetwork(self):
        return self._clinet

    def monitors(self):
        return self._monitors

    def hibernate(self):
        if self.current_run:
            self.current_run.hibernate( self.environment.run_controlfile )

    def setup_keys(self, execenv, sigbase, keysize, firstkeyno, nkeys):
        java = self.create_javacmd( execenv )

        java.classname( 'reptor.start.Configurator' ) \
                .argument( execenv.setupdir ) \
                .argument( sigbase, keysize, firstkeyno, nkeys )

        java.execute()

    def load_config(self, file):
        self._sysconf_mapping.read_from( file )

    def load_run(self, file):
        self.current_run = BenchmarkRun.load_benchmark_run( file )

    def load_current_run(self, execenv):
        self.load_run( execenv.run_controlfile )
        self.environment = execenv
        self.load_config( execenv.run_configfile )

    def setup_run(self, execenv):
        assert self.current_run is None
        self.current_run = BenchmarkRun( True )
        self.environment = execenv

        self._sysconf_mapping.write_to( execenv.run_configfile )
        self.hibernate()
        print( 'Created config {} ({})'.format( execenv.run_configfile, execenv.run_configfile.exists() ) )

    def start_replicas(self, mode=ProcessGroupStartMode.background, await_ready=True):
        self._replicas.start( self.environment, mode, await_ready )

    def start_clients(self, mode=ProcessGroupStartMode.background, await_ready=True):
        self._clients.start( self.environment, mode, await_ready )

    def start_monitors(self):
        self._monitors.init_monitors().start( self.environment )

    def start_benchmark(self):
        self.current_run.start( self.benchmark_settings.maximum_benchmarktime )
        self.hibernate()

        with socket.socket( socket.AF_INET, socket.SOCK_DGRAM ) as sock:
            for endpoint in self.controlnetwork.endpoints().of_processtype( SystemProcess ):
#                ep = proc.endpoints_by_network().endpoints( self._system.controlnetwork )[ 0 ].concrete_endpoint
                ep = endpoint.concrete_endpoint
                print( 'Send go {} ({})'.format( endpoint.process.name, ep ) )
                sock.sendto( bytearray( b'GO' ), (str(ep.address), ep.port) )

    def await_benchmark_finished(self):
        maxt = self.current_run.maximum_duration

        if maxt is None:
            timeout = Timeout.Infinite
        else:
            timeout = Timeout( maxt-time.time()+self.current_run.starttime.timestamp() ).start()

        self._clients.join( timeout )

        if self.benchmark_settings.replicas_finish_properly:
            self._replicas.join( timeout )
            self._monitors.join( timeout )

        self.current_run.finish()

    def await_httpbenchmark_finished(self):
        # maxt = self.current_run.maximum_duration
        #
        # if maxt is None:
        #     timeout = Timeout.Infinite
        # else:
        #     timeout = Timeout( maxt-time.time()+self.current_run.starttime.timestamp() ).start()

        # self._clients.join( timeout )

        timeout = Timeout( self.maximum_starttime( self.environment ) ).start()

        wait_by_polling( self, methodcaller( 'is_http_finished', self.environment ), timeout, 'Wait for client0 to finish.' )

        # if self.benchmark_settings.replicas_finish_properly:
        #     self._replicas.join( timeout )
        #     self._monitors.join( timeout )

        self.current_run.finish()

    def await_ycsbbenchmark_finished(self):
        # maxt = self.current_run.maximum_duration
        #
        # if maxt is None:
        #     timeout = Timeout.Infinite
        # else:
        #     timeout = Timeout( maxt-time.time()+self.current_run.starttime.timestamp() ).start()

        # self._clients.join( timeout )

        timeout = Timeout( self.maximum_starttime( self.environment ) ).start()

        wait_by_polling( self, methodcaller( 'is_finished', self.environment ), timeout, 'Wait for client0 to finish.' )

        # if self.benchmark_settings.replicas_finish_properly:
        #     self._replicas.join( timeout )
        #     self._monitors.join( timeout )

        self.current_run.finish()

    def is_http_finished(self, execenv):
        if self.logfile( execenv ).exists():
            with open("`pwd`/wrkdir/run/logs/01-log.log","r") as f:
                for line in f:
                    if 'summary =' in line:
                        return True
        return self._isfinished

    def is_finished(self, execenv):
        if self.logfile( execenv ).exists():
            with open("`pwd`/wrkdir/run/logs/client0-stdout.log","r") as f:
                for line in f:
                    if '[OVERALL]' in line:
                        return True
        return self._isfinished

    def logfile(self, execenv):
        return execenv.client_logfile( 0 )

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime

    def collect_benchmarkdata(self):
        for proc in self.entities_of_abstract_type( ServiceProcess ):
            proc.collect_benchmarkdata( self.environment )

    def save_benchmarkresults(self, name=None, desc=None):
        nameadd = '-'+name if name is not None else ''

        if self.benchmark_settings.protocol_variant!=Value.Undefined:
            protocol = self.benchmark_settings.protocol_variant
        else:
            protocol = self.global_settings.protocol

        resdirname = '{starttime:%Y_%m_%d-%H_%M_%S}-{protocol}-S{repsched}{name}-{client_count}'.format(
                         starttime=self.current_run.starttime,
                         protocol=protocol,
                         repsched=self.global_settings.replica_scheduler_config,
                         client_count=self.benchmark_settings.client_count,
                         name=nameadd )

        dstdir = self.environment.resultsdir / resdirname
        srcdir = self.environment.rundir

        print( 'Save {} to {}'.format( srcdir, dstdir ) )

        def _ignore_empty(path, names):
            def is_empty(path):
                return path.is_dir() and not any( path.iterdir() ) or not path.stat().st_size
            return [n for n in names if is_empty( Path( path, n ) )]

        shutil.copytree( str( srcdir ), str( dstdir ), ignore=_ignore_empty )

        descparts = []

        if desc:
            descparts.append( desc )

        def add_desc(key, val, defval):
            if val!=Value.Undefined and val!=defval:
                descparts.append( '{} {}'.format( key, val ) )

        add_desc( 'req', self.benchmark_settings.request_size, 0 )
        add_desc( 'reqspercli', self.benchmark_settings.requests_per_client, 1 )
        add_desc( 'rep', self.benchmark_settings.reply_size, 0 )
        add_desc( 'batmin', self.global_settings.batchsize_min, 1 )
        add_desc( 'batmax', self.global_settings.batchsize_max, 1 )
        add_desc( 'inst', self.global_settings.inst_dist, 'rr' )
        add_desc( 'rot', self.global_settings.rotate, 'false' )
        add_desc( 'reps', self.global_settings.repliers, str( len( self.replicas() ) ) )
        add_desc( 'chk', self.global_settings.checkpoint_mode, 'regular' )
        add_desc( 'app', self.benchmark_settings.servicename, 'zero' )

        if desc:
            with ( dstdir / 'results' / 'description.txt' ).open( 'w', encoding='utf-8', newline='') as df:
                df.write( '; '.join( descparts ) )

    def cleanup_run(self):
        self.current_run = None

        for proc in self.entities_of_abstract_type( SystemProcess ):
            proc.cleanup_run()

    def stop_processes(self):
        for proc in self.entities_of_abstract_type( SystemProcess ):
            proc.kill()

    def determine_concrete_processes(self):
        proctypes = self.subtypes_of_type( SystemProcess )

        for proc in psutil.process_iter():
            try:
                cmdargs = proc.cmdline()
            except psutil.AccessDenied:
                continue

            if cmdargs:
                for proctype in proctypes:
                    sysproc = proctype.determine_process( cmdargs, self )

                    if sysproc and (sysproc.concrete_process is None or sysproc.concrete_process.pid!=proc.pid):
                        print( 'Assign {} to {}'.format( proc.pid, sysproc.name ) )
                        sysproc.concrete_process = proc

    def kill_local_processes(self):
        proctypes = self.subtypes_of_type( SystemProcess )

        for proc in psutil.process_iter():
            try:
                cmdargs = proc.cmdline()
            except (psutil.AccessDenied, psutil.NoSuchProcess):
                continue

            if cmdargs:
                for proctype in proctypes:
                    procname = proctype.determine_processname( cmdargs, self )

                    if procname is not None:
                        print( 'Kill {} ({})'.format( procname, proc.pid ) )
                        try:
                            proc.kill()
                            proc.wait()
                        except Exception as e:
                            print( 'Error while killing {} ({}): {}'.format( procname, proc.pid, e ) )
                        break

    def create_javacmd(self, execenv):
        java = self.java_settings.create_cmd()
        java.classpath( execenv.builddir, execenv.libdir / '*', execenv.depsdir / '*', execenv.depsdir / 'trinx' / 'build' / 'classes' / 'main' / 'java' )
        for proj in ("base", "jlib"), ("base", "chronos"), ("exprmt", "measr"), ("distrbt", "distrbt"), \
                    ("replct", "replct"), ("reptor", "test"), ("replct", "smart"), ("replct", "bench"), \
                    ("reptor", "start"), ("tbft", "tbft"), ("tbft", "tbft-c"), ("", ""):
            java.classpath( execenv.builddir / os.path.join( *proj ) )

        if self.global_settings.trinx_library!=Value.Undefined:
            java.option( "-Djava.library.path=" + str( Path( self.global_settings.trinx_library ).parent ) )

        return java

    def get_identity(self, host, user=None):
        # TODO: default user
        return RemoteIdentity( RemoteLogin( host, user ), None )

    def create_sshcmd(self, execenv, host):
        return SSH( host )

    def create_scpcmd(self, execenv):
        return SCP()


class ReptorGlobalSettings:
    def __init__(self):
        self.protocol             = Value.Undefined
        self.replica_scheduler_config = Value.Undefined
        self.replica_scheduler_count  = Value.Undefined
        self.order_stage_count     = Value.Undefined
#        self.quorum               = Value.Undefined
#        self.weak_quorum          = Value.Undefined
#        self.replica_endpoints    = Value.Undefined
        self.batchsize_min        = Value.Undefined
        self.batchsize_max        = Value.Undefined
#        self.checkpoint_interval  = Value.Undefined
#        self.in_progress_factor   = Value.Undefined
        self.checkpoint_mode      = Value.Undefined
#        self.checkpoint_threshold = Value.Undefined
        self.inst_dist            = Value.Undefined
        self.rotate               = Value.Undefined
        self.repliers             = Value.Undefined
#        self.commit_threshold     = Value.Undefined
        self.client_prot          = Value.Undefined
        self.dist_contacts        = Value.Undefined
        self.clients_cert_algo    = Value.Undefined
        self.replies_cert_algo    = Value.Undefined
        self.replicas_strong_cert_algo   = Value.Undefined
        self.replicas_standard_cert_algo = Value.Undefined
        self.replicas_trusted     = Value.Undefined
        self.trinx_library         = Value.Undefined
        self.trinx_enclave         = Value.Undefined
        self.message_digest       = Value.Undefined
        self.ssl_algo             = Value.Undefined
        self.clients_ssl          = Value.Undefined
        self.replicas_ssl         = Value.Undefined
        self.dist_contacts        = Value.Undefined
        self.troxy                = Value.Undefined
        self.client_prot          = Value.Undefined


class JavaSettings:
    def __init__(self):
        self.heapmax   = '8g'
        self.heapstart = '8g'
        self.heapyoung = '6g'
        self.useserver = True
        self.enable_asserts = True

    def create_cmd(self):
        java = Java().maximum_heapsize( self.heapmax ).start_heapsize( self.heapstart ).young_heapsize( self.heapyoung ) \
                     .use_servervm( self.useserver )

        # TODO: Use new plib which supports that option
        if self.enable_asserts:
            java.option( '-ea' )

        return java


class ReptorBenchmarkSettings:
    def __init__(self):
        self.servicename = 'zero'
        # self.servicename = 'ycsb'
        # self.servicename = 'http'

        self.protocol_variant    = Value.Undefined
        self.benchmark_name      = Value.Undefined
        self.request_size        = Value.Undefined
        self.reply_size          = Value.Undefined
        self.requests_per_client = Value.Undefined
        self.viewchange_interval = Value.Undefined
#        self.state_size          = Value.Undefined
#        self.dummy_request_certs = Value.Undefined
#        self.verify_replies      = Value.Undefined
#        self.use_request_timeout = Value.Undefined
#        self.threaded_clients    = Value.Undefined
        self.client_count        = Value.Undefined

        self.zk_nnodes_client    = Value.Undefined
        self.zk_writerate        = Value.Undefined
        self.zk_nnodes           = Value.Undefined
        self.zk_dsmin            = Value.Undefined
        self.zk_dsmax            = Value.Undefined
        self.zk_hashblock        = Value.Undefined

        self.client_measure_transmitted_data     = Value.Undefined
        self.replica_measure_transmitted_data    = Value.Undefined
        self.replica_measure_executed_requests   = Value.Undefined
        self.replica_measure_applied_checkpoints = Value.Undefined
        self.replica_measure_processed_requests  = Value.Undefined
        self.replica_measure_consensus_instances = Value.Undefined

        self.warmup_time   = None
        self.run_time      = None
        self.cooldown_time = None

        self.process_affinity = Value.Undefined
        self.ncores           = Value.Undefined

    @property
    def duration(self):
        return self.warmup_time + self.run_time + self.cooldown_time if self.run_time is not None else None

    @duration.setter
    def duration(self, value):
        if value is None:
            self.warmup_time = self.run_time = self.cooldown_time = None
        else:
            self.warmup_time   = round( value*0.6 )
            self.cooldown_time = round( value*0.1 )
            self.run_time      = value-self.warmup_time-self.cooldown_time

    @property
    def has_maximum_benchmarktime(self):
        return self.run_time is not None

    @property
    def maximum_benchmarktime(self):
        if not self.has_maximum_benchmarktime:
            return None
        else:
            return self.duration + 20

    @property
    def replicas_finish_properly(self):
        return any( (self.replica_measure_transmitted_data, self.replica_measure_executed_requests,
                    self.replica_measure_applied_checkpoints, self.replica_measure_processed_requests,
                    self.replica_measure_consensus_instances) )


class ReptorProcessGroupMixin(metaclass=ABCMeta):
    @property
    @abstractmethod
    def name(self):
        ...

    def start(self, execenv, mode=ProcessGroupStartMode.background, await_ready=True):
        # TODO: logger, events, UI callbacks?
        print( 'Start ' + self.name )

        if mode==ProcessGroupStartMode.background:
            for proc in self:
                proc.print_stdout = False
            start_procs_in_background( self )
        else:
            assert Tmux.runs_within_session()

            if mode==ProcessGroupStartMode.windows:
                start_procs_in_tmux_windows( self )
            else:
                start_procs_in_tmux_panes( self.name, self )

        if await_ready:
            self.await_ready( execenv )

        if mode!=ProcessGroupStartMode.background:
            # TODO: When a terminal multiplexer like tmux is used, commands are executed not directly but
            #       by the session server process. The return PID is just for the process that sends the
            #       commands to the server and is close afterwards.
            #       Solutions: 1. Write PID into a file and wait for that file, 2. Insert a marker into
            #       the command, e.g. echo $guid > /dev/null. If multiple parallel session are to be supported,
            #       the working directory could get a GUID and become something like a experiment controller.
            #       To find started processes, the own ancestors could be traversed to find the session server.
            #       All started processes are children of it.
            self._system.determine_concrete_processes()

    def await_ready(self, execenv):
        timeout = Timeout( self.maximum_starttime( execenv ) ).start()
        print( 'Wait for ' + self.name )

        for proc in self:
            # if self.name == 'replicas':
            #     proc.await_ready( execenv, timeout )
            proc.await_ready( execenv, timeout )
            print( '{} is ready'.format( proc.name ) )

    def join(self, timeout):
        for proc in self:
            print( 'Wait for {}'.format( proc.name ) )
            proc.join( timeout )


class ReptorReplicaGroup(ReplicaGroup, ReptorProcessGroupMixin):
    def __init__(self):
        super().__init__( ReptorReplica )

    @property
    def name(self):
        return 'replicas'

    def maximum_starttime(self, execenv):
        return execenv.replicas_maximum_starttime


class ReptorBenchmarkClientGroup(BenchmarkClientGroup, ReptorProcessGroupMixin):
    def __init__(self):
        super().__init__( ReptorClient )
        # super().__init__( ProphecyClient )
        # super().__init__( YCSBClient )
        # super().__init__( HTTPClient )

    @property
    def name(self):
        return 'clients'

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime


class ReptorProcessMixin(metaclass=ABCMeta):
    def __init__(self):
        self.print_stdout = True

    # TODO: Pipeline: ssh( procdir( main-cmd ) )
    def create_command(self, local=False):
        execenv = self.system.environment
        cmd = self._create_command( execenv )

        if self.system.benchmark_settings.process_affinity!=Value.Undefined:
            cmd = NUMACtl( cmd ).corelist( self.system.benchmark_settings.process_affinity )

        procdir = self.processdir( execenv )
        cmd = DeleteFile( procdir ).recursively().force() & CreateDir( procdir ).with_parents() & ChangeDir( procdir ) & cmd

        if not local and not self.host.concrete_host.is_local:
            cmd = self._system.create_sshcmd( execenv, self.host.concrete_host ).command( cmd )

        if self.print_stdout:
            cmd = ( cmd > StdErrToStdOut ) | Tee( self.logfile( execenv ) )
        else:
            cmd = cmd > ToFile( self.logfile( execenv ) )

        print( 'Start {} on {}'.format( self.name, self.host.concrete_host.name ) )

        return cmd

    def collect_benchmarkdata(self, execenv):
        print( 'Collect data for {} from {}'.format( self.name, self.host.concrete_host ) )

        scp = self._system.create_scpcmd( execenv ) \
                                .dest( execenv.run_resdir ) \
                                .recursively()

        if self.host.concrete_host.is_local:
            scp.source( self.processdir( execenv ) )
        else:
            ident = self._system.get_identity( self.host.concrete_host )
            scp.keyfile( ident.credentials )
            scp.source( RemotePath( ident.login, self.processdir( execenv ) ) )

        scp.execute()


# Expects:
#   namepattern, logfile, number
#
class ReptorServiceProcessMixin(ReptorProcessMixin, JavaProcess):
    def __init__(self):
        ReptorProcessMixin.__init__( self )
        JavaProcess.__init__( self )
        self._isready = False

    @property
    def name(self):
        return self.namepattern.format( self.number )

    ready_magic = b'<<READY>>'

    def is_ready(self, execenv):
        if not self._isready:
            self._isready = self.logfile( execenv ).exists() and \
                            subprocess.Popen( ('grep', '-F', self.ready_magic, str( self.logfile( execenv ) )), stdout=subprocess.PIPE ) \
                                      .communicate()[ 0 ]==self.ready_magic + b'\n'
        return self._isready

    def cleanup_run(self):
        self.concrete_process = None
        self._isready = False

    def await_ready(self, execenv, timeout=None):
        if timeout is None:
            timeout = Timeout( self.maximum_starttime( execenv ) ).start()

        wait_by_polling( self, methodcaller( 'is_ready', execenv ), timeout, 'Wait for {} to be ready.'.format( self.name ) )

    @classmethod
    def determine_processname(cls, cmdargs, system):
        # TODO: Parse arguments with Java(ShellCommand) to ensure that really the classname is compared
        no = cls.determine_number( cmdargs )
        return None if no is None else cls.namepattern.format( no )

    @classmethod
    def determine_number(cls, cmdargs):
        # TODO: Parse arguments with Java(ShellCommand) to ensure that really the classname is compared
        if Path( cmdargs[ 0 ] ).name not in ('java', 'ssh') or cls.classname not in cmdargs:
            return None
        else:
            return int( cmdargs[ cmdargs.index( cls.classname )+1 ] )

    @classmethod
    def determine_process(cls, cmdargs, system):
        no = cls.determine_number( cmdargs )

        if no is None:
            return None
        else:
            return cls.group_for_type( system )[ no ]

    def _append_benchmarktimes(self, java):
        bs = self._system.benchmark_settings
        if not bs.has_maximum_benchmarktime:
            java.argument( 0, -1, 0 )
        else:
            java.argument( bs.warmup_time, bs.run_time, bs.cooldown_time )


class ReptorReplica(Replica, ReptorServiceProcessMixin):
    def __init__(self, group, no, host):
        Replica.__init__( self, group, no, host )
        ReptorServiceProcessMixin.__init__( self )

    namepattern = 'replica{}'

    def logfile(self, execenv):
        return execenv.replica_logfile( self.number )

    def processdir(self, execenv):
        return execenv.replica_processdir( self.number )

    def maximum_starttime(self, execenv):
        return execenv.replicas_maximum_starttime

    @classmethod
    def group_for_type(cls, system):
        return system.replicas()

    classname = 'reptor.start.ReplicaHost'

    def _create_command(self, execenv):
        java = self.system.create_javacmd( execenv )

        java.option( '-Dlogback.configurationFile=' + str( execenv.logging_configfile ) )

        if self.system.benchmark_settings.zk_nnodes!=Value.Undefined:
            java.option( '-Dzk.nnodes=' + str( self.system.benchmark_settings.zk_nnodes ) )

        if self.system.benchmark_settings.zk_dsmin!=Value.Undefined:
            java.option( '-Dzk.dsmin=' + str( self.system.benchmark_settings.zk_dsmin ) )

        if self.system.benchmark_settings.zk_dsmax!=Value.Undefined:
            java.option( '-Dzk.dsmax=' + str( self.system.benchmark_settings.zk_dsmax ) )

        if self.system.benchmark_settings.zk_hashblock!=Value.Undefined:
            java.option( '-Dzk.hashblock=' + str( self.system.benchmark_settings.zk_hashblock ) )

        java.classname( self.classname ) \
                .argument( self.number, execenv.run_configfile ) \
                .argument( self.system.benchmark_settings.servicename )
        self._append_benchmarktimes( java )
        java.argument( execenv.run_resdir )

        return java


class YCSBClient(BenchmarkClient, ReptorServiceProcessMixin):
    def __init__(self, group, no, host):
        BenchmarkClient.__init__(self, group, no, host)
        ReptorServiceProcessMixin.__init__(self)

    namepattern = 'client{}'

    def logfile(self, execenv):
        return execenv.client_logfile( self.number )

    def processdir(self, execenv):
        return execenv.client_processdir( self.number )

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime

    classname = 'com.yahoo.ycsb.Client'

    @classmethod
    def group_for_type(cls, system):
        return system.clients()

    def _create_command(self, execenv):
        java = self.system.create_javacmd(execenv)

        java.option( '-Dlogback.configurationFile=' + str( execenv.logging_configfile ) )

        java.classname(self.classname) \
            .argument( '-threads ' + str(self.system.benchmark_settings.client_count) ) \
            .argument( '-P ' + str( execenv.configdir / 'ycsb' / 'workloads' / 'workloada' ) ) \
            .argument( '-p operationcount=' + str(10000*self.system.benchmark_settings.client_count) ) \
            .argument( '-p measurementtype=timeseries' ) \
            .argument( '-p timeseries.granularity=1000' ) \
            .argument( '-db reptor.bench.apply.ycsb.YCSBClient') \
            .argument( '-t -s' )

        return java


class HTTPClient(BenchmarkClient, ReptorServiceProcessMixin):
    def __init__(self, group, no, host):
        BenchmarkClient.__init__(self, group, no, host)
        ReptorServiceProcessMixin.__init__(self)

    namepattern = 'client{}'

    def logfile(self, execenv):
        return execenv.client_logfile( self.number )

    def processdir(self, execenv):
        return execenv.client_processdir( self.number )

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime

    classname = 'reptor.bench.apply.http.HttpClient'

    @classmethod
    def group_for_type(cls, system):
        return system.clients()

    def _create_command(self, execenv):
        java = self.system.create_javacmd(execenv)

        java.option( '-Dlogback.configurationFile=' + str( execenv.logging_configfile ) )

        java.classname(self.classname).argument( self.number, execenv.run_configfile )
        java.argument( execenv.run_resdir )

        return java


class ProphecyClient(BenchmarkClient, ReptorServiceProcessMixin):
    def __init__(self, group, no, host):
        BenchmarkClient.__init__(self, group, no, host)
        ReptorServiceProcessMixin.__init__(self)

    namepattern = 'client{}'

    def logfile(self, execenv):
        return execenv.client_logfile( self.number )

    def processdir(self, execenv):
        return execenv.client_processdir( self.number )

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime

    classname = 'reptor.bench.apply.zero.ProphecyClient'

    @classmethod
    def group_for_type(cls, system):
        return system.clients()

    def _create_command(self, execenv):
        java = self.system.create_javacmd(execenv)

        java.option( '-Dlogback.configurationFile=' + str( execenv.logging_configfile ) )

        java.classname(self.classname).argument( self.number, execenv.run_configfile )
        java.argument( execenv.run_resdir )

        return java


class ReptorClient(BenchmarkClient, ReptorServiceProcessMixin):
    def __init__(self, group, no, host):
        BenchmarkClient.__init__( self, group, no, host )
        ReptorServiceProcessMixin.__init__( self )

    namepattern = 'client{}'

    def logfile(self, execenv):
        return execenv.client_logfile( self.number )

    def processdir(self, execenv):
        return execenv.client_processdir( self.number )

    def maximum_starttime(self, execenv):
        return execenv.clients_maximum_starttime

    classname = 'reptor.start.BenchmarkerHost'

    @classmethod
    def group_for_type(cls, system):
        return system.clients()

    def _create_command(self, execenv):
        java = self.system.create_javacmd( execenv )

        java.option( '-Dlogback.configurationFile=' + str( execenv.logging_configfile ) )

        if self.system.benchmark_settings.zk_writerate!=Value.Undefined:
            java.option( '-Dzk.writerate=' + str( self.system.benchmark_settings.zk_writerate ) )

        if self.system.benchmark_settings.zk_dsmin!=Value.Undefined:
            java.option( '-Dzk.dsmin=' + str( self.system.benchmark_settings.zk_dsmin ) )

        if self.system.benchmark_settings.zk_dsmax!=Value.Undefined:
            java.option( '-Dzk.dsmax=' + str( self.system.benchmark_settings.zk_dsmax ) )

        if self.system.benchmark_settings.zk_nnodes_client!=Value.Undefined:
            java.option( '-Dzk.nodes=' + str( self.system.benchmark_settings.zk_nnodes_client ) )


        java.classname( self.classname ).argument( self.number, execenv.run_configfile )
        self._append_benchmarktimes( java )
        java.argument( 0, self._system.benchmark_settings.servicename, execenv.run_resdir )

        return java


def wait_by_polling(obj, cond, timeout, curact, interval=1.0):
    while True:
        if cond( obj ):
            break

        timeout.check( curact )

        time.sleep( interval )


class ReptorMonitorGroup(SystemEntityGroup, ReptorProcessGroupMixin):
    def __init__(self):
        super().__init__()

        self._monitors = {}

        self.monitor_per_nic    = True
        self.monitor_per_cpu    = True
        self.monitor_per_thread = False

    def _dispose(self, disset):
        self._monitors.clear()
        super()._dispose( disset )

    def __contains__(self, x):
        return isinstance( x, ReptorMonitorProcess )

    def __len__(self):
        return len( self._monitors )

    def ordered(self):
        for host in sorted( self._monitors.keys() ):
            yield self._monitors[ host ]

    def __iter__(self):
        yield from self._monitors.values()

    @property
    def name(self):
        return 'monitors'

    def init_monitors(self):
        hosts = set()

        for group in self.system.replicas(), self.system.clients():
            for proc in group:
                ch = proc.host.concrete_host

                if ch in hosts:
                    continue

                hosts.add( ch )
                self._monitors[ ch ] = ReptorMonitorProcess( proc.host, proc.name, self ).register( self.system )

        return self

    def await_ready(self, execenv):
        pass

    def join(self, timeout):
        for proc in self:
            if not proc.concrete_process:
                print( 'Process for {} is unknown'.format( proc.name ) )
            else:
                print( 'Wait for {}'.format( proc.name ) )
                proc.join( timeout )



class ReptorMonitorProcess(ReptorProcessMixin, SystemProcess, ShellProcess):
    def __init__(self, host, hostname, group):
        ReptorProcessMixin.__init__( self )
        SystemProcess.__init__( self, host )
        ShellProcess.__init__( self )

        self.group    = group
        self.hostname = hostname

    @property
    def name(self):
        return 'monitor_{}'.format( self.hostname )

    def cleanup_run(self):
        self.concrete_process = None

    def _create_command(self, execenv):
        cmd = GenericShellCommand( execenv.scriptdir / '_monitor.py' )

        cmd.argument( 'start', '-h', self.hostname )
        cmd.argument( '-d', execenv.run_resdir )

        if self.group.monitor_per_nic:
            cmd.argument( '-n' )
        if self.group.monitor_per_cpu:
            cmd.argument( '-c' )
        if self.group.monitor_per_thread:
            cmd.argument( '-t' )

        return cmd

    @classmethod
    def determine_processname(cls, cmdargs, system):
        hostname = cls.determine_hostname( cmdargs, system )

        return None if hostname is None else 'monitor_{}'.format( hostname )

    @classmethod
    def determine_hostname(cls, cmdargs, sytem):
        if len( cmdargs )<5 or Path( cmdargs[ 1 ] ).name!='_monitor.py':
            return None
        else:
            return cmdargs[ cmdargs.index( '-h' )+1 ]

    @classmethod
    def determine_process(cls, cmdargs, system):
        # TODO: Problems: host names are not unique and monitor processes require concrete hosts.
        return None

    def processdir(self, execenv):
        return execenv.monitor_processdir( self )

    def logfile(self, execenv):
        return execenv.monitor_logfile( self )
