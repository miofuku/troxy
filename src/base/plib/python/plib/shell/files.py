#!/usr/bin/env python3.4

from pathlib import Path

from plib.shell import SingleShellCommand


class FileCommands:
    def __init__(self):
        self.ls_path    = Path( 'ls' )
        self.cd_path    = Path( 'cd' )
        self.mkdir_path = Path( 'mkdir' )
        self.rm_path    = Path( 'rm' )

    def create_ls_command(self):
        return ListDir( cmdpath=self.ls_path )

    def create_cd_command(self):
        return ChangeDir( cmdpath=self.cd_path )

    def create_mkdir_command(self):
        return CreateDir( cmdpath=self.mkdir_path )

    def create_rm_command(self):
        return DeleteFile( cmdpath=self.rm_path )


class ListDir(SingleShellCommand):
    def __init__(self, *paths, cmdpath='ls'):
        super().__init__( cmdpath )

        self._paths = list( paths )

    def path(self, *paths):
        return self.extend_paths( paths )

    def extend_paths(self, paths):
        self._paths.extend( paths )
        return self

    def get_paths(self):
        return self._paths

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        args.extend( self._paths )

        return self._str( args )


class ChangeDir(SingleShellCommand):
    def __init__(self, path=None, cmdpath='cd'):
        super().__init__( cmdpath )

        self._path = path

    def path(self, path):
        self._path = path
        return self

    def get_path(self):
        return self._path

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if self._path is not None:
            args.append( self._path )

        return self._str( args )


class CreateDir(SingleShellCommand):
    def __init__(self, *paths, cmdpath='mkdir'):
        super().__init__( cmdpath )

        self._paths = list( paths )
        self._withparents = False

    def path(self, *paths):
        return self.extend_paths( paths )

    def extend_paths(self, paths):
        self._paths.extend( paths )
        return self

    def get_paths(self):
        return self._paths

    def with_parents(self, value=True):
        self._withparents = value
        return self

    def get_with_parents(self):
        return self._withparents

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if self._withparents:
            args.append( '-p' )

        args.extend( self._paths )

        return self._str( args )


class DeleteFile(SingleShellCommand):
    def __init__(self, *paths, cmdpath='rm'):
        super().__init__( cmdpath )

        self._paths = list( paths )
        self._recursively = False
        self._force = False

    def path(self, *paths):
        return self.extend_paths( paths )

    def extend_paths(self, paths):
        self._paths.extend( paths )
        return self

    def get_paths(self):
        return self._paths

    def recursively(self, value=True):
        self._recursively = value
        return self

    def get_recursively(self):
        return self._recursively

    def force(self, value=True):
        self._force = value
        return self

    def get_force(self):
        return self._force

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        opts = '-'

        if self._recursively:
            opts += 'r'
        if self._force:
            opts += 'f'

        if len( opts )>1:
            args.append( opts )

        args.extend( self._paths )

        return self._str( args )
