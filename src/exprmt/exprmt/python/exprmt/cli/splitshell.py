#!/usr/bin/env python3.4

import re
from subprocess import CalledProcessError

from consl import Consl
from consl.cmds import CommandGrammarFamily
from exprmt.cli.networks import NetworkConfigConslCLI, ShowNetworkConfig
from intrpt.valacts import StoreList
from plib.shell.ssh import SSH
from plib.shell.tmux import Tmux


# TODO: Turn into regular shell command, but extract layouts and move them into tmux.
class SplitHostShellCmdBuilder:
    class StateError(Exception):
        pass

    def __init__(self, hostcmd=None, sessname=None, hosts=None, network=None, window_mode=False):
        self.session_name = sessname
        self.hosts        = hosts or []
        self.host_cmd     = hostcmd
        self.network      = network
        self.window_mode  = window_mode

    def set_session_name(self, sessname):
        self.session_name = sessname
        return self

    def set_host_cmd(self, sb):
        self.host_cmd = sb
        return self

    def set_window_mode(self, value):
        self.window_mode = value
        return self

    def add_host(self, host):
        self.hosts.append( host )
        return self

    def clear_hosts(self):
        self.hosts.clear()
        return self


    def create_cmd(self):
        if not self.hosts:
            raise SplitHostShellCmdBuilder.StateError( 'No hosts were given.' )

        cb = self._create_window_cmd() if self.window_mode else self._create_pane_cmd()

        return cb


    def _create_window_cmd(self):
        cb = Tmux()

        if Tmux.runs_within_session():
            cb.new_window( self.hosts[ 0 ].name )
        else:
            cb.new_session( self.session_name, windowname=self.hosts[ 0 ].name )

        if self.host_cmd:
            self._send_host_cmd( cb, self.hosts[ 0 ] )

        for host in self.hosts[ 1: ]:
            cb.new_window( host.name )
            if self.host_cmd:
                self._send_host_cmd( cb, host )

        cb.select_window( self.hosts[ 0 ].name )

        return cb


    def _create_pane_cmd(self):
        if len( self.hosts )>6:
            raise SplitHostShellCmdBuilder.StateError( 'A maximum number of 6 hosts is currently supported.' )

        cb = Tmux()

        if Tmux.runs_within_session():
            cb.new_window( self.session_name, panename=self.hosts[ 0 ].name )
        else:
            cb.new_session( self.session_name, panename=self.hosts[ 0 ].name )

        oh = Tmux.Orientation.horizontal
        ov = Tmux.Orientation.vertical

        nh = len( self.hosts )

        if nh>3:
            cb.split_window( orient=ov, target=self.hosts[ 0 ].name, panename=self.hosts[ 2 if nh<6 else 3 ].name )
        if nh>1 and nh<6:
            cb.split_window( orient=oh, target=self.hosts[ 0 ].name, panename=self.hosts[ 1 ].name )
        if nh==3:
            cb.split_window( orient=ov, target=self.hosts[ 1 ].name, panename=self.hosts[ 2 ].name )
        elif nh==4:
            cb.split_window( orient=oh, target=self.hosts[ 2 ].name, panename=self.hosts[ 3 ].name )
        elif nh==5:
            cb.split_window( orient=oh, target=self.hosts[ 2 ].name, panename=self.hosts[ 3 ].name, perc=67 )
            cb.split_window( orient=oh, target=self.hosts[ 3 ].name, panename=self.hosts[ 4 ].name )
        elif nh==6:
            cb.split_window( orient=oh, target=self.hosts[ 0 ].name, panename=self.hosts[ 1 ].name, perc=67 )
            cb.split_window( orient=oh, target=self.hosts[ 1 ].name, panename=self.hosts[ 2 ].name )
            cb.split_window( orient=oh, target=self.hosts[ 3 ].name, panename=self.hosts[ 4 ].name, perc=67 )
            cb.split_window( orient=oh, target=self.hosts[ 4 ].name, panename=self.hosts[ 5 ].name )

        if self.host_cmd:
            for host in self.hosts:
                self._send_host_cmd( cb, host, host.name )

        if nh>1:
            cb.sync_panes().select_pane( self.hosts[ 0 ].name )

        return cb


    def _send_host_cmd(self, cb, host, target=None):
        if self.network is None:
            addr = host
        else:
            addr = host.ipaddrs_by_network[ self.network ][ 0 ].hostaddr

        cs = self.host_cmd.address( addr ).create_str()
        cb.send_keys( [cs, Tmux.SpecialKey.enter], target=target )



def determine_session_name(sessbase):
    rerunsess = re.compile( sessbase + r'(\d*)\n' )

    def filter_sessionnos(line):
        m = rerunsess.fullmatch( line )
        return (True, int(m.group( 1 )) if m.group( 1 ) else 1) if m else (False, None)

    try:
        sessnos = Tmux().list_sessionnames().filtered_call( filter_sessionnos )
    except CalledProcessError as e:
        if e.returncode==1:
            sessnos = None
        else:
            raise e

    sessname = sessbase
    if sessnos:
        sessname += str( max( sessnos )+1 )

    return sessname


class SplitShell(Consl, NetworkConfigConslCLI):
    def __init__(self, netconfig, progname='splitshell', config=CommandGrammarFamily):
        Consl.__init__( self, None )

        self._netconfig = netconfig

        self._init_grammar( config, progname )

    def _init_grammar(self, config, progname):
        main  = self._create_splitshell_main( config, progname )
        help_ = self._create_help_fallback( config, progname )

        self._set_grammar( main, help_ )

    @property
    def networkconfig(self):
        return self._netconfig

    def _create_splitshell(self, args, hosts, sessname):
        ssh = SSH( user=args.user )

        cmd = SplitHostShellCmdBuilder( ssh, sessname, hosts, args.network, args.window_mode ).create_cmd()

        if args.print_only:
            print( cmd.create_str() )
        else:
            cmd.execute()

    def create_hostgroup_shell(self, args):
        self._create_splitshell( args, args.hostgroup.hosts, determine_session_name( args.hostgroup.name ) )

    def create_hosts_shell(self, args):
        self._create_splitshell( args, args.hosts, None )

    def _create_splitshell_main(self, config, name='splitshell'):
        conopts = self._create_connection_options( config )

        return self._create_main( config, name ) \
                        .abstract( 'Connect to hosts in a split shell window' ) \
                        .description( 'splitshell can be used to connect to groups of hosts via tmux sessions with properly configured window panes.' ) \
                        .start_subcommands() \
                            .add_expression( self._create_help_cmd( config ) ) \
                            .add_expression( self._create_show_cmd( config ) ) \
                            .add_expression( self._create_hosts_cmd( config, conopts ) ) \
                            .add_expression( self._create_hostgroup_cmd( config, conopts ) ) \
                            .end()

    def _create_show_cmd(self, config, name='show'):
        return ShowNetworkConfig( self, name, config )

    def _create_hostgroup_cmd(self, config, conopts, name='group'):
        return config.create_command( name ) \
                        .abstract( 'Connect to a host group' ) \
                        .description( 'Creates a split shell to all hosts within a group.' ) \
                        .call( self.create_hostgroup_shell ) \
                        .prepend_name_element( False ) \
                        .start_constant( 'group' ).optional().end() \
                        .start_variable( 'hostgroup' ).add_processor( self.process_hostgroup ).end() \
                        .extend_expressions( conopts )

    def _create_hosts_cmd(self, config, conopts, name='hosts'):
        return config.create_command( name ) \
                        .abstract( 'Connect to given hosts' ) \
                        .description( 'Creates a split shell to all specified hosts.' ) \
                        .call( self.create_hosts_shell ) \
                        .start_variable( 'host' ) \
                            .at_least_once() \
                            .add_processor( self.process_host ) \
                            .destination( 'hosts' ) \
                            .standard_action( StoreList ) \
                            .end() \
                        .extend_expressions( conopts )

    def _create_connection_options(self, config):
        usropt = config.create_variable_option( 'user' ).alias( 'u' ).description( 'Login used for SSH connection.' )

        netopt = config.create_variable_option( 'network' ).alias( 'n' ) \
                        .description( 'Network if hosts have multiple NICs.' ) \
                        .add_processor( self.process_network )

        modopt = config.create_flaggroup() \
                        .flag( 'print-only', 'p', desc='Print only the resulting shell command.' ) \
                        .flag( 'window-mode', 'w', desc='Use multiple windows instead of split panes.' )

        return usropt, netopt, modopt
