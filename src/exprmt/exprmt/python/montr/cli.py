#!/usr/bin/env python3.4

from pathlib import Path
import re
import shutil
import socket

from consl import Consl
from consl.cmds import CommandGrammarFamily, CommandExpression
from intrpt.valprocs import RangeChecker, Converter
from measr.sysres import MeasuredBytes
from montr import ResourceMonitor
from montr.translt_out import save_results


class MonitorResourcesCLI:
    def __init__(self):
        self.prevprocs = []
        self.prevhost  = None
        self.termsize  = shutil.get_terminal_size( (80, 20) )
        self.nics      = []
        self.nicfilter = None

    _interval_format = '{:3d} '

    def set_nicfilter(self, nicfilter):
        self.nicfilter = re.compile( nicfilter ) if nicfilter else None

    def print_interval(self, monitor, intno):
        if intno==0:
            if monitor.hostrecorder:
                self.prevhost = self._format_host( monitor, intno, monitor.hostrecorder, None, None, None )

                if monitor.monitor_per_nic:
                    self.nics = sorted( self.prevhost.net.nic_counters.keys() )

                    if self.nicfilter:
                        self.nics = [nic for nic in self.nics if self.nicfilter.match( nic )]

            for procrec in monitor.procrecorders:
                self.prevprocs.append( self._format_host( monitor, intno, procrec, None, None, None ) )

            fmtelems = fmtargs = []
        elif monitor.hostrecorder and len( monitor.procrecorders )==1:
            fmtelems = ['{} ']
            fmtargs  = [intno]

            self._format_proc( monitor, intno, monitor.procrecorders[ 0 ], self.prevprocs[ 0 ], fmtelems, fmtargs )
            fmtelems.append( '   ' )
            self._format_host( monitor, intno ,monitor.hostrecorder, self.prevhost, fmtelems, fmtargs )

            print( ''.join( fmtelems ).format( *fmtargs ) )
        else:
            if monitor.hostrecorder:
                self._print_interval( intno, self._format_host, monitor, monitor.hostrecorder, self.prevhost )

            for i in range( 0, len( monitor.procrecorders ) ):
                self._print_interval( intno, self._format_proc, monitor,
                                      monitor.procrecorders[ i ], self.prevprocs[ i ] )

    def _print_interval(self, intno, formatter, monitor, recorder, previous):
            fmtelems = [self._interval_format]
            fmtargs  = [intno]

            formatter( monitor, intno, recorder, previous, fmtelems, fmtargs )

            print( ''.join( fmtelems ).format( *fmtargs ) )

    def _format_proc(self, monitor, intno, recorder, previous, fmtelems, fmtargs):
        current = recorder.process_monitoring_point( recorder.results[ -1 ], previous )

        if previous:
            fmtelems.append( '{}:' )
            fmtargs.append( recorder.name )

            fmtelems.append( ' {:3.0f} % (cpu)' )
            fmtargs.append( current.cpu.load*100 )

            fmtelems.append( ' {:4.0f} MB (mem)' )
            fmtargs.append( MeasuredBytes.to_megabytes( current.mem.values.rss ) )

        return current

    def _format_host(self, monitor, intno, recorder, previous, fmtelems, fmtargs):
        current = recorder.process_monitoring_point( recorder.results[ -1 ], previous )

        if previous:
            fmtelems.append( '{}:' )
            fmtargs.append( recorder.name )

            fmtelems.append( ' {:3.0f} % (cpu)' )
            fmtargs.append( current.cpu.load*100 )

            if monitor.monitor_per_cpu:
                for load in current.cpu.detailed_loads:
                    fmtelems.append( ' {:3.0f}' )
                    fmtargs.append( load*100 )
                fmtelems.append( ' (cores)' )

            cpu = self._format( fmtelems, fmtargs )

            netres = current.net.result
            fmtelems.append( ' {:6.1f} {:6.1f} (tx/rx MB/s)' )
            fmtargs.extend( ( netres.sent.megabytes_per_sec, netres.recv.megabytes_per_sec ) )

            if monitor.monitor_per_nic:
                for nic in self.nics:
                    res = current.net.nic_results[ nic ]
                    fmtelems.append( ' {:6.1f} {:6.1f} ({})' )
                    fmtargs.extend( ( res.sent.megabytes_per_sec, res.recv.megabytes_per_sec, nic ) )

            net = self._format( fmtelems, fmtargs )

            if len( cpu ) + len( net ) <= self.termsize.columns:
                fmtelems.extend( (cpu, net) )
            else:
                print( cpu )
                fmtelems.extend( (' '*len( self._interval_format.format( intno ) ), net) )

        return current

    def _format(self, fmtelems, fmtargs):
        s = ''.join( fmtelems ).format( *fmtargs )

        fmtelems.clear()
        fmtargs.clear()

        return s

    def listen_to(self, monitor):
        def decorate(intno):
            self.print_interval( monitor, intno )
        monitor.interval_finished = decorate


class MonitorResources(CommandExpression):
    def __init__(self, monitor_factory=None, cli=None, name='monitor', hostmode=False, config=CommandGrammarFamily):
        super().__init__( name, config )

        self.monitor_factory = monitor_factory or ResourceMonitor
        self.cli = cli or MonitorResourcesCLI()

        self._init_cmd( hostmode )

    def run_monitor(self, args):
        monitor = self.monitor_factory()

        if args.monitor_procs:
            monitor.monitor_per_thread = args.perthread

            # TODO: An exception should be thrown if no proceses are found. However, this requires that
            #       the default value for monitor-procs is configurable or that this mode can be disabled
            #       completely.
            monitor.init_processes()

        if args.monitor_host:
            monitor.monitor_host( args.host_name )
            monitor.monitor_per_cpu = args.percpu
            monitor.monitor_per_nic = args.pernic

        if args.print_ints:
            self.cli.listen_to( monitor )

        if args.nic_filter:
            self.cli.set_nicfilter( args.nic_filter )

        monitor.interval          = args.interval
        monitor.maximum_intervals = args.max_intervals

        recs = monitor.run()

        if args.save_results:
            save_results( recs, args.result_dir, args.file_pattern )

    def _init_cmd(self, hostmode):
        self.abstract( 'Monitor platform resources' ) \
            .call( self.run_monitor )

        self.start_variable_option( 'interval', 'i' ) \
                .description( 'Sample interval in fractions of seconds.' ) \
                .convert_float() \
                .add_processor( RangeChecker( 0.0, inclmin=False ) ) \
                .use_default_processors( False ) \
                .default_value( 1.0 )

        self.start_option( 'max-intervals', 'm' ) \
                .description( 'The maximum number of intervals to be monitored.' ) \
                .start_choice() \
                    .start_variable( 'max-intervals' ) \
                        .default_value( None ) \
                        .convert_int() \
                        .add_processor( RangeChecker( 1 ) ) \
                        .use_default_processors( False ) \
                        .end() \
                    .constant( 'none' )

        self.start_option( 'result-dir', 'd' ) \
                .description( 'Path at which the results shall be saved.' ) \
                .start_variable( 'result-dir' ).add_processor( Converter( Path, True ) ).default_value( Path( '.' ) ).end()

        self.start_variable_option( 'file-pattern', 'f' ) \
                .description( 'Pattern for generated files. {rectype} and {recname} are replaced by '
                              'the type and the name of the resource monitor, respectively.' ) \
                .default_value( 'resources_{rectype}_{recname}.log' )

        self.start_variable_option( 'monitor-host', 'h' ) \
                .description( 'Host that shall be monitored.' ) \
                .configure_key().value( True ).default_value( hostmode ).end() \
                .configure_variable().optional().destination( 'host_name').default_value( socket.gethostname() )

        self.variable_option( 'nic-filter', desc='Filters NICs according to the given name pattern.' )

        self.start_flaggroup() \
                .switch( 'print-ints', 'not-print-ints', ('e',), ('E',), desc='Print interval summaries.', default_value=hostmode ) \
                .switch( 'save-results', 'not-save-results', ('s',), ('S',), desc='Save all measured values.', default_value=True ) \
                .switch( 'percpu', 'not-percpu', ('c',), ('C',), desc='Monitor load per core.' ) \
                .switch( 'pernic', 'not-pernic', ('n',), ('N',), desc='Monitor rates per NIC.' ) \
                .switch( 'perthread', 'not-perthread', ('t',), ('T',), desc='Monitor load per thread.' ) \
                .switch( 'monitor-procs', 'do-not-monitor-procs', ('p',), ('P',), desc='Monitor processes at the host.', default_value=True )

        def final_check(cntxt, expr):
            args = cntxt.args_store
            if not args.monitor_procs and not args.monitor_host:
                cntxt.on_error( expr, msg='At least processes or the host have to be monitored' )

        self.configure_scope().configure_finalize_events().add_action( final_check )


# TODO: Quite generic -> Consl for list of subcommands?
class ResourceMonitorConsl(Consl):
    def __init__(self, monfac=None, cli=None, progname='monitor', hostmode=False, config=CommandGrammarFamily):
        super().__init__( None )

        self._init_grammar( config, progname, hostmode, monfac, cli )

    def _init_grammar(self, config, progname, hostmode, monfac, cli):
        main  = self._create_monitor_main( config, progname, hostmode, monfac, cli )
        help_ = self._create_help_fallback( config, progname )

        self._set_grammar( main, help_ )

    def _create_monitor_main(self, config, name, hostmode, monfac, cli):
        return self._create_main( config, name ) \
                        .abstract( 'Monitor platform and process resources' ) \
                        .start_subcommands() \
                            .add_expression( self._create_help_cmd( config ) ) \
                            .add_expression( MonitorResources( monfac, cli, 'start', hostmode ) ) \
                            .end()
