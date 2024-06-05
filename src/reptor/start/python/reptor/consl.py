#!/usr/bin/env python3.4

from consl import Consl
from consl.cmds import CommandGrammarFamily
from exprmt.cli.networks import NetworkConfigConslCLI, ShowNetworkConfig
from intrpt.valacts import OverrideStore
from montr.cli import MonitorResources
from plib.networks import Host
from plib.shell import GenericShellCommand
from plib.shell.tmux import Tmux
from reptor.monitor import ReptorResourceMonitor
from reptor.system import ProcessGroupStartMode
from reptor.bench import Benchmarker


class ReptorConsl(Consl, NetworkConfigConslCLI):
    def __init__(self, execenv, progname='reptor', config=CommandGrammarFamily):
        super().__init__( None )

        self.environment = execenv
        self.system      = execenv.system

        self._runcfg_loaded = False

        self._init_grammar( config, progname )

    @property
    def networkconfig(self):
        return self.environment.netenv.networkconfig if self.environment.netenv else None

    def _init_grammar(self, config, progname):
        main  = self._create_reptor_main( config, progname )
        # TODO: integrate into main as choice?
        help_ = self._create_help_fallback( config, progname )

        self._set_grammar( main, help_ )

    # TODO: Separate commands similar to MontiorResources
    def _create_reptor_main(self, config, name='reptor'):
        return self._create_main( config, name ) \
                        .abstract( 'Benchmark the Reptor prototype' ) \
                        .description( 'This is the main script to control benchmarks on basis of the Reptor prototype.' ) \
                        .start_subcommands() \
                            .add_expression( self._create_help_cmd( config ) ) \
                            .add_expression( self._create_show_cmd( config ) ) \
                            .add_expression( self._create_setup_cmd( config ) ) \
                            .add_expression( self._create_start_cmd( config ) ) \
                            .add_expression( self._create_benchmark_cmd( config ) ) \
                            .add_expression( self._create_kill_cmd( config ) ) \
                            .add_expression( self._create_cleanup_cmd( config ) ) \
                            .add_expression( MonitorResources( ReptorResourceMonitor ) ) \
                            .end()

    def _create_show_cmd(self, config, name='show'):
        return ShowNetworkConfig( self, name, config )

    def _create_setup_cmd(self, config, name='setup'):
        return config.create_command( name ) \
                        .abstract( 'Initialize the setup' ) \
                        .start_choice() \
                            .add_expression( self._create_setup_netenv_cmd( config ) ) \
                            .add_expression( self._create_setup_logging_cmd( config ) ) \
                            .add_expression( self._create_setup_keys_cmd( config ) ) \
                            .add_expression( self._create_setup_run_cmd( config ) ) \
                            .end()

    def setup_netenv(self, args):
        self.cleanup_run( args )
        self.environment.setup_netenv_by_name( args.netenv )

    def _create_setup_netenv_cmd(self, config, name='netenv'):
        return config.create_command( name ) \
                        .abstract( 'Set default network environment configuration' ) \
                        .variable( 'netenv' ) \
                        .call( self.setup_netenv )

    def _ensure_netenv(self, args):
        if self.environment.netenv is None:
            args.netenv = 'local'
            self.setup_netenv( args )

    def setup_logging(self, args):
        self.environment.setup_logging_by_name( args.logging )

    def _create_setup_logging_cmd(self, config, name='logging'):
        return config.create_command( name ) \
                        .abstract( 'Set default logging configuration' ) \
                        .variable( 'logging' ) \
                        .call( self.setup_logging )

    def setup_keys(self, args):
        self.system.setup_keys( self.environment, args.sigbase, args.keysize, args.firstkeyno, args.nkeys )

    def _create_setup_keys_cmd(self, config, name='keys'):
        return config.create_command( name ) \
                        .abstract( 'Create key pairs' ) \
                        .variable( 'sigbase' ) \
                        .start_variable( 'keysize' ).convert_int().end() \
                        .start_variable( 'firstkeyno' ).convert_int().end() \
                        .start_variable( 'nkeys' ).convert_int().end() \
                        .call( self.setup_keys )

    def setup_run(self, args):
        self._ensure_netenv( args )
        print( 'Prepare {}'.format( self.environment.rundir ) )

        self.environment.load_base_config()

        self.init_replica_config( args )
        self.init_benchmark_options( args )

        self.environment.setup_run()
        self._runcfg_loaded = True

    def _create_setup_run_cmd(self, config, name='run'):
        cmd = config.create_command( name ) \
                        .abstract( 'Set up a new benchmark run' ) \
                        .call( self.setup_run )

        self._add_replica_options( cmd )
        self._add_benchmark_options( cmd )

        return cmd

    def _create_cleanup_cmd(self, config, name='cleanup'):
        return config.create_command( name ) \
                        .abstract( 'Cleanup the setup' ) \
                        .start_choice() \
                            .add_expression( self._create_cleanup_output_cmd( config ) ) \
                            .add_expression( self._create_cleanup_run_cmd( config ) ) \
                            .add_expression( self._create_cleanup_netenv_cmd( config ) ) \
                            .end()

    def cleanup_run(self, args):
        self.environment.cleanup_run()

    def _create_cleanup_run_cmd(self, config, name='run'):
        return config.create_command( name ) \
                        .abstract( 'Reset the current benchmark run' ) \
                        .call( self.cleanup_run )

    def cleanup_netenv(self, args):
        self.environment.cleanup_netenv()

    def _create_cleanup_netenv_cmd(self, config, name='netenv'):
        return config.create_command( name ) \
                        .abstract( 'Cleanup the default network environment config' ) \
                        .call( self.cleanup_netenv )

    def _create_cleanup_output_cmd(self, config, name='output'):
        return config.create_command( name ) \
                        .abstract( 'Cleanup all output of the current run' ) \
                        .paramcall( self.environment.reset_run )

    def _create_start_cmd(self, config, name='start'):
        return config.create_command( name ) \
                        .abstract( 'Start processes' ) \
                        .start_choice() \
                            .add_expression( self._create_start_replicas_cmd( config ) ) \
                            .add_expression( self._create_start_monitors_cmd( config ) ) \
                            .add_expression( self._create_start_clients_cmd( config ) ) \
                            .add_expression( self._create_start_all_cmd( config ) ) \
                            .add_expression( self._create_start_benchmark_cmd( config ) ) \
                            .end()

    def _load_runconfig(self, args):
        if self._runcfg_loaded:
            return

        if self.environment.netenv is None:
            raise Exception( 'Cannot load run config without network config.' )
        self.environment.load_run()
        self._runcfg_loaded = True

    def start_replicas(self, args):
        if self.environment.run_configfile.exists():
            self._load_runconfig( args )
        else:
            self.setup_run( args )

        self.system.start_replicas( mode=args.replicas_window_mode )

    def _create_windowmode_option(self, config, name, dest, defval=ProcessGroupStartMode.background):
        return config.create_option( name ) \
                        .start_selection( name ) \
                            .default_destination( dest ) \
                            .default_value( defval ) \
                            .alternative( 'background', 'b', store_value=ProcessGroupStartMode.background ) \
                            .alternative( 'windows', 'w', store_value=ProcessGroupStartMode.windows ) \
                            .alternative( 'panes', 'p', store_value=ProcessGroupStartMode.panes ) \
                            .end()

    def init_replica_config(self, args):
        c = False

        if args.prot is not None:
            self.environment.set_protocol_config_by_name( args.prot )
            c = True

        self.environment.load_protocol_config()

        if args.repsched is not None:
            self.environment.set_replica_scheduler_config_by_name( args.repsched )
            self.system.global_settings.replica_scheduler_config = args.repsched
            c = True

        if args.clisched is not None:
            self.environment.set_client_scheduler_config_by_name( args.clisched )
            c = True

        self.environment.load_scheduler_config()

        for pn in 'batchsize_min', 'batchsize_max', 'checkpoint_mode', 'inst_dist', 'rotate', 'repliers', 'troxy':
            if pn in args and args[ pn ] is not None:
                setattr( self.system.global_settings, pn, args[ pn ] )
                c = True

        for pn, an in ('tm', 'replicas_trusted'), ('repcert', 'replicas_strong_cert_algo'), \
                      ('stdcert', 'replicas_standard_cert_algo'), ('clicert', 'clients_cert_algo'), \
                      ('repliescert', 'replies_cert_algo'), \
                      ('msgdig', 'message_digest'), ('trinxlib', 'trinx_library'), ('trinxenc', 'trinx_enclave'), \
                      ('ssl', 'ssl_algo'), ('repssl', 'replicas_ssl'), ('clissl', 'clients_ssl'), \
                      ('cliprot', 'client_prot'), ('clidist', 'dist_contacts'):
            if pn in args and args[ pn ] is not None:
                setattr( self.system.global_settings, an, args[ pn ] )
                c = True

        for pn, an in ('vcint', 'viewchange_interval'), ('app', 'servicename'), ('zk_dsmin', 'zk_dsmin'), \
                      ('zk_dsmax', 'zk_dsmax'), ('zk_hashblock', 'zk_hashblock'), ('zk_nnodes', 'zk_nnodes'):
            if pn in args and args[ pn ] is not None:
                setattr( self.system.benchmark_settings, an, args[ pn ] )
                c = True

        return c


    def _add_replica_options(self, cmd):
        cmd.variable_option( 'ssl' )
        cmd.variable_option( 'repssl' )
        cmd.variable_option( 'clissl' )
        cmd.variable_option( 'repsched' )
        cmd.variable_option( 'clisched' )
        cmd.variable_option( 'prot' )
        cmd.variable_option( 'cliprot' )
        cmd.variable_option( 'clidist' )
        cmd.start_variable_option( 'batchsize-min' ).convert_int()
        cmd.start_variable_option( 'batchsize-max' ).convert_int()
        cmd.variable_option( 'checkpoint_mode' )
        cmd.variable_option( 'inst_dist' )
        cmd.variable_option( 'rotate' )
        cmd.variable_option( 'repliers' )
        cmd.variable_option( 'tm' )
        cmd.variable_option( 'repcert' )
        cmd.variable_option( 'stdcert' )
        cmd.variable_option( 'clicert' )
        cmd.variable_option( 'repliescert' )
        cmd.variable_option( 'msgdig' )
        cmd.variable_option( 'trinxlib' )
        cmd.variable_option( 'trinxenc' )
        cmd.variable_option( 'troxy' )
        cmd.start_variable_option( 'vcint' ).convert_int()
        cmd.variable_option( 'app' )

        cmd.start_variable_option( 'zk-dsmin' ).convert_int()
        cmd.start_variable_option( 'zk-dsmax' ).convert_int()
        cmd.start_variable_option( 'zk-nnodes' ).convert_int()
        cmd.start_variable_option( 'zk-hashblock' ).convert_int()


    # TODO: ValueCall(Store, Evaluation)? Precedence?
    def init_benchmark_options(self, args):
        c = False

        if args.clihosts is not None:
            self.system.clients().set_count( args.clihosts )
            c = True

        if args.clients is not None:
            self.system.benchmark_settings.client_count = args.clients
            c = True

        if args.reqs_per_client is not None:
            self.system.benchmark_settings.requests_per_client = args.reqs_per_client
            c = True

        if args.reqsize is not None:
            self.system.benchmark_settings.request_size = args.reqsize
            c = True

        if args.repsize is not None:
            self.system.benchmark_settings.reply_size = args.repsize
            c = True

        if args.zk_writerate is not None:
            self.system.benchmark_settings.zk_writerate = args.zk_writerate
            c = True

        if args.zk_nnodes_client is not None:
            self.system.benchmark_settings.zk_nnodes_client = args.zk_nnodes_client
            c = True

        return c

    def _add_benchmark_options(self, cmd):
        cmd.start_variable_option( 'clihosts' ).convert_int()
        cmd.start_variable_option( 'clients' ).convert_int()
        cmd.start_variable_option( 'reqs-per-client' ).convert_int()
        cmd.start_variable_option( 'reqsize' ).convert_int()
        cmd.start_variable_option( 'repsize' ).convert_int()

        cmd.start_variable_option( 'zk-writerate' ).convert_int()
        cmd.start_variable_option( 'zk-nnodes-client' ).convert_int()

    def set_duration_config(self, args):
        c = False

        if args.duration is not None:
            self.system.benchmark_settings.duration = args.duration
            c = True
        else:
            if args.warmuptime is not None:
                self.system.benchmark_settings.warmup_time = args.warmuptime
                c = True
            if args.runtime is not None:
                self.system.benchmark_settings.run_time = args.runtime
                c = True
            if args.cooldowntime is not None:
                self.system.benchmark_settings.cooldown_time = args.cooldowntime
                c = True

        return c

    def _add_benchtimes_options(self, cmd):
        cmd.start_option( 'duration' ) \
                .any_number() \
                .start_choice() \
                    .backtracking( True ) \
                    .start_sequence() \
                        .start_variable( 'warmuptime' ).standard_action( OverrideStore ).convert_int().end() \
                        .start_variable( 'runtime' ).standard_action( OverrideStore ).convert_int().end() \
                        .start_variable( 'cooldowntime' ).standard_action( OverrideStore ).convert_int().end() \
                        .store_const( 'duration', None, True ) \
                        .end() \
                    .start_variable( 'duration' ) \
                        .default_value( None ) \
                        .standard_action( OverrideStore ) \
                        .store_const( 'warmuptime', None, True ) \
                        .store_const( 'runtime', None, True ) \
                        .store_const( 'cooldowntime', None, True ) \
                        .convert_int() \
                        .end()

        def _add_time(name):
            cmd.start_variable_option( name ) \
                    .any_number() \
                    .configure_variable() \
                        .standard_action( OverrideStore ) \
                        .store_const( 'duration', None, True ) \
                        .convert_int() \
                        .end()

        _add_time( 'cooldowntime')
        _add_time( 'runtime' )
        _add_time( 'warmuptime' )


    def _create_start_replicas_cmd(self, config, name='replicas'):
        return config.create_command( name ) \
                        .abstract( 'Start replicas' ) \
                        .call( self.start_replicas ) \
                        .add_expression( self._create_windowmode_option( config, 'window-mode', 'replicas_window_mode' ) )

    def start_monitors(self, args):
        if not self.environment.run_configfile.exists():
            raise Exception( 'Cannot start monitors without run config.' )

        self._load_runconfig( args )

        if args.monitor_per_thread:
            self.system.monitors().monitor_per_thread = True

        self.system.start_monitors()

    def _create_start_monitors_cmd(self, config, name='monitors'):
        return config.create_command( name ) \
                        .abstract( 'Start monitors' ) \
                        .call( self.start_monitors )

    def start_clients(self, args):
        if not self.environment.run_configfile.exists():
            raise Exception( 'Cannot start clients without run config.' )

        self._load_runconfig( args )
        self.system.start_clients( mode=args.clients_window_mode )

    def _create_start_clients_cmd(self, config, name='clients'):
        return config.create_command( name ) \
                        .abstract( 'Start clients' ) \
                        .call( self.start_clients ) \
                        .add_expression( self._create_windowmode_option( config, 'window-mode', 'clients_window_mode' ) )

    def start_all_processes(self, args):
        self.start_replicas( args )
        self.start_clients( args )
        self.start_monitors( args )

    def _create_start_all_cmd(self, config, name='all'):
        defwm = ProcessGroupStartMode.panes if Tmux.runs_within_session else ProcessGroupStartMode.background

        return config.create_command( name ) \
                        .abstract( 'Start all processes' ) \
                        .call( self.start_all_processes ) \
                        .add_expression( self._create_windowmode_option( config, 'replicas-window-mode', 'replicas_window_mode', defwm ) ) \
                        .add_expression( self._create_windowmode_option( config, 'clients-window-mode', 'clients_window_mode', defwm ) )

    def start_benchmark(self, args):
        if args.newrun:
            self.setup_run( args )

        repsstarted = self.environment.replica_logfile( 0 ).exists()
        clisstarted = self.environment.client_logfile( 0 ).exists()

        c = False
        c = self.init_replica_config( args ) or c
        c = self.init_benchmark_options( args ) or c
        c = self.set_duration_config( args ) or c

        assert not c or (not repsstarted and not clisstarted)

        if not repsstarted:
            self.start_replicas( args )

            if args.monitor:
                self.start_monitors( args )
        if not clisstarted:
            self.start_clients( args )

        self._load_runconfig( args )
        self.system.start_benchmark()

        if self.system.benchmark_settings.servicename == 'http':
            self.system.await_httpbenchmark_finished()
            self._kill_all_processes(self.system)
            self.system.stop_processes()

        if self.system.benchmark_settings.servicename == 'ycsb':
            self.system.await_ycsbbenchmark_finished()
            self._kill_all_processes(self.system)
            self.system.stop_processes()

        if self.system.benchmark_settings.servicename == 'zero':
            if not args.finish and not args.save_results:
                return
            self.system.await_benchmark_finished()
            self.system.collect_benchmarkdata()

        if not args.save_results:
            return

        self.system.save_benchmarkresults( args.name )

    def _kill_all_processes(self, system):
        hosts = self.environment.netenv.all_concrete_hosts( system )

        remcmd = GenericShellCommand( self.environment.controlscript, 'kill', '--local' )

        for host in hosts:
            if host.is_local:
                system.kill_local_processes()
            else:
                print( 'Connect to {}'.format( host ) )
                system.create_sshcmd( self.environment, host ).command( remcmd ).execute()

    def _create_start_benchmark_cmd(self, config, name='benchmark'):
        defwm = ProcessGroupStartMode.panes if Tmux.runs_within_session else ProcessGroupStartMode.background

        cmd = config.create_command( name ) \
                        .abstract( 'Start benchmark' ) \
                        .call( self.start_benchmark ) \
                        .switch( 'newrun', 'no-newrun', default_value=True ) \
                        .switch( 'monitor', 'dont-monitor', default_value=True ) \
                        .switch( 'monitor-per-thread', 'dont-monitor-per-thread', default_value=True ) \
                        .flag( 'finish' ) \
                        .start_option( 'save-results' ) \
                            .configure_key().value( True ).default_value( False ).end() \
                            .start_variable( 'name' ).optional().default_value( None ).end() \
                            .end() \
                        .add_expression( self._create_windowmode_option( config, 'replicas-window-mode', 'replicas_window_mode', defwm ) ) \
                        .add_expression( self._create_windowmode_option( config, 'clients-window-mode', 'clients_window_mode', defwm ) )

        self._add_replica_options( cmd )
        self._add_benchmark_options( cmd )
        self._add_benchtimes_options( cmd )

        return cmd

    def collect_benchmarkdata(self, args):
        self._load_runconfig( args )
        self.system.collect_benchmarkdata()

    def _create_collect_data_cmd(self, config, name='collect'):
        return config.create_command( name ) \
                        .abstract( 'Collect benchmark data' ) \
                        .call( self.collect_benchmarkdata )

    def reset_run(self, args):
        args.host = None
        self.kill_procs( args )
        self.environment.reset_run()

    def _create_reset_run_cmd(self, config, name='reset'):
        return config.create_command( name ) \
                        .abstract( 'Reset the current run' ) \
                        .call( self.reset_run )

    def _create_benchmark_cmd(self, config, name='bench'):
        return config.create_command( name ) \
                        .abstract( 'Control benchmark' ) \
                        .start_choice() \
                            .add_expression( self._create_start_benchmark_cmd( config, 'start' ) ) \
                            .add_expression( self._create_collect_data_cmd( config ) ) \
                            .add_expression( self._create_reset_run_cmd( config ) ) \
                            .add_expression( self._create_finish_run_cmd( config ) ) \
                            .add_expression( self._create_save_results_cmd( config ) ) \
                            .add_expression( self._create_benchmark_series_cmd( config ) ) \
                            .end()

    def _create_benchmark_series_cmd(self, config, name='series'):
        return config.create_command( name ) \
                        .abstract( 'Runs benchmark series' ) \
                        .call( self.run_series ) \
                        .variable( 'platform' ) \
                        .flag( 'dry-run' ) \
                        .start_variable_option( 'duration' ).default_value( None ).convert_int().end()

    def run_series(self, args):
        bench = Benchmarker( self.environment, args.platform )

        if args.duration is not None:
            bench.config.duration = args.duration

        bench.execute( args.dry_run )

    def _create_save_results_cmd(self, config, name='save'):
        return config.create_command( name ) \
                        .abstract( 'Save benchmark results' ) \
                        .call( self.save_results ) \
                        .start_variable( 'name' ).optional().end()

    def _create_finish_run_cmd(self, config, name='finish'):
        return config.create_command( name ) \
                        .abstract( 'Finish the current run' ) \
                        .call( self.finish_run )

    def finish_run(self, args):
        self._load_runconfig( args )

        if self.system.current_benchmark is None:
            self.system.load_current_benchmark()

        self.system.determine_concrete_processes()
        self.system.await_benchmark_finished()
        self.system.collect_benchmarkdata()

    def save_results(self, args):
        self._load_runconfig( args )

        self.system.save_benchmarkresults( args.name )

    def kill_procs(self, args):
        if args.host and args.host.is_local:
            self.system.kill_local_processes()
        else:
            if args.host:
                hosts = (args.host,)
            else:
                self._ensure_netenv( args )
                hosts = self.environment.netenv.all_concrete_hosts( self.system )

            remcmd = GenericShellCommand( self.environment.controlscript, 'kill', '--local' )

            for host in hosts:
                if host.is_local:
                    self.system.kill_local_processes()
                else:
                    print( 'Connect to {}'.format( host ) )
                    self.system.create_sshcmd( self.environment, host ).user( args.user ).command( remcmd ).execute()

    def _create_kill_cmd(self, config, name='kill'):
        return config.create_command( name ) \
                        .abstract( 'Kill running processes' ) \
                        .call( self.kill_procs ) \
                        .start_option( 'local' ).configure_key().store_const( 'host', Host.Local ).end().end() \
                        .start_variable_option( 'host' ).add_processor( self.process_host ).end() \
                        .variable_option( 'user' )
