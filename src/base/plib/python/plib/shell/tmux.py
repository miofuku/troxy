#!/usr/bin/env python3.4

from enum import Enum
import os
from pathlib import Path

from plib.shell import SingleShellCommand, ShellCommandContext, ShellCommand, FixedShellCommand


class TmuxCommands:
    def __init__(self):
        self.tmux_path = Path( 'tmux' )

    def create_tmux_command(self):
        return Tmux( cmdpath=self.tmux_path )


# TODO: Fluid interface for windows and panes (start_window, start_pane)?
class Tmux(SingleShellCommand):
    @classmethod
    def runs_within_session(cls):
        return 'TMUX' in os.environ

    def __init__(self, cmdpath='tmux'):
        super().__init__( cmdpath )

        self._tmuxcmds       = []
        self._windows        = []
        self._windownames    = {}
        self._current_window = None

    class Orientation(Enum):
        horizontal = '-h'
        vertical   = '-v'

    class SpecialKey(Enum):
        enter = 'ENTER'

    class _Window:
        def __init__(self, number, name):
            self.number    = number
            self.name      = name
            self.panenames = {}

        def pane_added(self, panename):
            pno = len( self.panenames )
            key = panename or str(pno)
            if key in self.panenames:
                raise KeyError( 'Pane ' + key + ' already exists.' )

            self.panenames[ key ] = str(pno)

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        for i, tmuxcmd in enumerate( self._tmuxcmds ):
            if i:
                if cntxt is None:
                    cntxt = ShellCommandContext()
                cntxt.enter_subcommand()
                args.append( cntxt.mask_operator( ';' ) )
                cntxt.leave_subcommand()
            args.extend( tmuxcmd.create_cmdargs( cntxt ) )

        return self._str( args )

    class _FixedSubcommand(ShellCommand):
        __slots__ = '_args', '_shellcmd'

        def __init__(self, args, shellcmd):
            super().__init__()
            self._args     = args
            self._shellcmd = shellcmd

        def create_cmdargs(self, cntxt):
            args = []
            args.extend( self._args )

            if cntxt and cntxt.within_shell:
                args.append( cntxt.mask_operator( '"' ) )

            args.append( self._shellcmd.create_str( None ) )

            if cntxt and cntxt.within_shell:
                args.append( cntxt.mask_operator( '"' ) )

            return self._str( args )

    def add_subcmd(self, cmdargs, shellcmd=None):
        cmd = FixedShellCommand( cmdargs ) if shellcmd is None else self._FixedSubcommand( cmdargs, shellcmd )
        self._tmuxcmds.append( cmd )
        return self

    def list_sessionnames(self):
        return self.add_subcmd( ('ls', '-F', '#S') )

    def list_windownames(self):
        return self.add_subcmd( ('list-windows', '-F', '#W') )

    def init_main_window(self, name=None, panename=None):
        self._create_window( name, panename )
        return self

    def new_session(self, name=None, windowname=None, panename=None, shellcmd=None):
        assert self._current_window is None

        cmdargs = ['new']
        if name is not None:
            cmdargs.extend( ('-s', name) )
        self._add_window( cmdargs, windowname )

        self._create_window( windowname, panename )

        return self.add_subcmd( cmdargs, shellcmd )

    def new_window(self, name=None, panename=None, shellcmd=None, printinfo=False):
        cmdargs = ['neww']

        if printinfo:
            cmdargs.append( '-P' )

        self._add_window( cmdargs, name )

        self._create_window( name, panename )

        return self.add_subcmd( cmdargs, shellcmd )

    def select_window(self, target):
        self._current_window = self._windownames[ target ] if target in self._windownames else self._windows[ target ]
        return self.add_subcmd( ('selectw', '-t', str( target )) )

    def _create_window(self, name=None, panename=None):
        window = self._Window( len( self._windows ), name )
        window.pane_added( panename )

        self._windows.append( window )
        if name is not None:
            self._windownames[ name ] = window
        self._current_window = window

        return window

    def split_window(self, perc=None, orient=Orientation.horizontal, target=None, panename=None, shellcmd=None):
        cmdargs = ['splitw', orient.value]
        if perc is not None:
            cmdargs.extend( ('-p', str( perc )) )
        self._add_target( cmdargs, target )

        self._current_window.pane_added( panename )

        return self.add_subcmd( cmdargs, shellcmd )

    def select_pane(self, target):
        cmdargs = ['selectp']
        self._add_target( cmdargs, target )
        return self.add_subcmd( cmdargs )

    def send_keys(self, keys, target=None):
        cmdargs = ['send-keys']
        self._add_target( cmdargs, target )
        for k in keys:
            cmdargs.append( k.value if isinstance( k, Tmux.SpecialKey ) else k )

        return self.add_subcmd( cmdargs )

    def sync_panes(self):
        return self.add_subcmd( ('set', 'synchronize-panes') )

    def remain_on_exit(self):
        return self.add_subcmd( ('set', '-g', 'remain-on-exit') )

    @classmethod
    def _add_window(cls, cmdargs, window):
        if window is not None:
            cmdargs.extend( ('-n', window) )

    def _add_target(self, cmdargs, target):
        if target is not None:
            cmdargs.extend( ('-t', self._current_window.panenames.get( target, target )) )
