#!/usr/bin/env python3.4

from collections import namedtuple
from pathlib import Path

from plib.networks import Addressable
from plib.shell import SingleShellCommand, ShellCommandContext


class SshCommands:
    def __init__(self):
        self.ssh_path = Path( 'ssh' )
        self.scp_path = Path( 'scp' )

    def create_ssh_command(self):
        return SSH( cmdpath=self.ssh_path )

    def create_scp_command(self):
        return SCP( cmdpath=self.scp_path )


class RemoteLogin(namedtuple( 'RemoteLogin', 'address user' ), Addressable):
    __slots__ = ()

    @classmethod
    def connectstr(cls, address, user):
        return user + '@' + str( address ) if user is not None else str( address )

    def __str__(self):
        return self.connectstr( self.address, self.user )


class RemoteIdentity(namedtuple( 'RemoteIdentity', 'login credentials')):
    __slots__ = ()


class RemotePath(namedtuple( 'RemotePath', 'address path' )):
    __slots__ = ()

    def __str__(self):
        return '{}:{}'.format( self.address, self.path )


# TODO: What about a different remote environment?
class SSHBased(SingleShellCommand):
    def __init__(self, cmdpath):
        super().__init__( cmdpath )

        self._keyfile = None

    def keyfile(self, keyfile):
        self._keyfile = keyfile
        return self

    def get_keyfile(self):
        return self._keyfile

    def _create_cmdargs_base(self):
        args = [self.get_cmdpath()]

        if self._keyfile is not None:
            args.extend( ('-i', self._keyfile) )

        return args


class SSH(SSHBased):
    def __init__(self, addr=None, cmd=None, user=None, cmdpath='ssh'):
        super().__init__( cmdpath )

        self._addr  = addr
        self._user  = user
        self._cmd   = cmd

    def login(self, remlogin):
        self._addr  = remlogin.address
        self._user  = remlogin.user

    def get_login(self):
        return None if self._addr is None else RemoteLogin( self._addr, self._user )

    def address(self, addr):
        self._addr = addr
        return self

    def get_address(self):
        return self._addr

    def user(self, user):
        self._user = user
        return self

    def get_user(self):
        return self._user

    def command(self, cmd):
        self._cmd = cmd
        return self

    def get_command(self):
        return self._cmd

    def create_cmdargs(self, cntxt):
        if not self._addr:
            raise ValueError( 'Address not initialized.' )

        args = self._create_cmdargs_base()

        args.append( self.get_login() )

        if self._cmd is not None:
            if cntxt is None:
                cntxt = ShellCommandContext()

            cntxt.enter_subcommand()
            args.extend( self._cmd.create_cmdargs( cntxt ) )
            cntxt.leave_subcommand()

        return self._str( args )


class SCP(SSHBased):
    def __init__(self, dest=None, *sources, cmdpath='scp'):
        super().__init__( cmdpath )

        self._sources     = list( sources )
        self._dest        = dest
        self._recursively = False

    def recursively(self, value=True):
        self._recursively = value
        return self

    def get_recursively(self):
        return self._recursively

    def source(self, *paths):
        self._sources.extend( paths )
        return self

    def get_sources(self):
        return self._sources

    def dest(self, path):
        self._dest = path
        return self

    def get_dest(self):
        return self._dest

    def create_cmdargs(self, cntxt):
        assert self._sources and self._dest

        args = self._create_cmdargs_base()

        if self._recursively:
            args.append( '-r' )

        for src in self._sources:
            args.append( src )

        args.append( self._dest )

        return self._str( args )
