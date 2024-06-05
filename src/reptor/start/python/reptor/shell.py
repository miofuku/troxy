#!/usr/bin/env python3.4

from plib.shell import SingleShellCommand, ShellCommandContext


class NUMACtl(SingleShellCommand):
    def __init__(self, cmd=None, cmdpath='numactl'):
        super().__init__( cmdpath )

        self._corelist = None
        self._cmd      = cmd

    def corelist(self, corelist):
        self._corelist = corelist
        return self

    def get_corelist(self):
        return self._corelist

    def command(self, cmd):
        self._cmd = cmd
        return self

    def get_command(self):
        return self._cmd

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if self._corelist is not None:
            args.extend( ('-C', self._corelist) )

        if cntxt is None:
            cntxt = ShellCommandContext()

        cntxt.enter_subcommand()
        args.extend( self._cmd.create_cmdargs( cntxt ) )
        cntxt.leave_subcommand()

        return self._str( args )


class Bash(SingleShellCommand):
    def __init__(self, cmd=None, cmdpath='bash'):
        super().__init__( cmdpath )

        self._cmd = cmd

    def command(self, cmd):
        self._cmd = cmd
        return self

    def get_command(self):
        return self._cmd

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if cntxt is None:
            cntxt = ShellCommandContext()

        args.append( '-c' )

        cntxt.enter_subcommand()
        args.append( self._cmd.create_str( cntxt ) )
        cntxt.leave_subcommand()

        return self._str( args )
