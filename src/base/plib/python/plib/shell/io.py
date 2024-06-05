#!/usr/bin/env python3.4

from pathlib import Path

from plib.shell import SingleShellCommand


class IOCommands:
    def __init__(self):
        self.tee_path  = Path( 'tee' )
        self.echo_path = Path( 'echo' )

    def create_tee_command(self):
        return Tee( cmdpath=self.tee_path )

    def create_echo_command(self):
        return Echo( cmdpath=self.echo_path )


class Tee(SingleShellCommand):
    def __init__(self, *files, append=False, cmdpath='tee'):
        super().__init__( cmdpath )

        self._files  = list( files )
        self._append = append

    def file(self, *paths):
        return self.extend_files( paths )

    def extend_files(self, paths):
        self._files.extend( paths )
        return self

    def get_files(self):
        return self._files

    def append_to_files(self, value):
        self._append = value
        return self

    def get_append_to_files(self):
        return self._append

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if self._append:
            args.append( '-a' )

        args.extend( self._files )

        return self._str( args )


class Echo(SingleShellCommand):
    def __init__(self, *values, newline=True, cmdpath='echo'):
        super().__init__( cmdpath )

        self._values  = list( values )
        self._newline = newline

    def value(self, *values):
        return self.extend_values( values )

    def extend_values(self, values):
        self._values.extend( values )
        return self

    def get_values(self):
        return self._values

    def append_newline(self, value):
        self._newline = value
        return self

    def get_append_newline(self):
        return self._append

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if not self._newline:
            args.append( '-n' )

        args.extend( self._values )

        return self._str( args )
