#!/usr/bin/env python3.4

from abc import abstractmethod
from pathlib import Path
from subprocess import CalledProcessError, TimeoutExpired
import subprocess
import sys
import time

from plib.utils.builders import FluidMixin


class Timeout:
    Infinite = None

    __slots__ = 'seconds', 'endtime'

    def __init__(self, seconds):
        self.seconds = seconds
        self.endtime = None

    def start(self):
        self.endtime = time.monotonic()+self.seconds
        return self

    def remaining(self):
        return self.endtime-time.monotonic()

    def check(self, curact):
        if self.remaining()<=0:
            raise TimeoutExpired( curact, self.seconds )


class __TimeoutInfinite(Timeout):
    __slots__ = ()

    def __init__(self):
        self.seconds = None
        self.endtime = None

    def start(self):
        return self

    def remaining(self):
        return sys.maxsize

    def check(self, curact):
        pass

Timeout.Infinite = __TimeoutInfinite()


class ShellEnvironment:
    def __init__(self, cmds):
        self.cmds = cmds


class ShellCommandCreationContext:
    def __init__(self, depth=-1):
        self._depth = depth

    @property
    def within_shell(self):
        return self._depth>=0

    def enter_subcommand(self):
        self._depth +=1

    def leave_subcommand(self):
        self._depth -=1

    # TODO: This is more a task of the environment, isn't it?
    def mask_operator(self, operator):
        if self._depth<1:
            return operator
        else:
            return self.escapeprefix + self.escapeprefix.join( operator )

    @property
    def escapeprefix(self):
        return '\\'*( ( self._depth-1 )*2 + 1 )


class ShellCommandContext(ShellCommandCreationContext):
    @classmethod
    def start_command(cls, cmd, stdin=None, stdout=None, stderr=None):
        cntxt = cls()

        cmd.start( cntxt=cntxt, stdin=stdin, stdout=stdout, stderr=stderr )

        return cntxt

    @classmethod
    def for_process(cls, proc):
        cntxt = cls()

        cntxt.process_started( proc )

        return cntxt

    def __init__(self):
        super().__init__()

        self._procs = []
        self._files = []

    @property
    def last_pid(self):
        return self._procs[ -1 ].pid if self._procs else -1

    def process_started(self, proc):
        self._procs.append( proc )

    def file_opened(self, file):
        self._files.append( file )

    def kill_processes(self, close=True):
        for proc in self._procs:
            try:
                proc.kill()
            except Exception:
                pass

        if close:
            self.close()

    def wait_for_processes(self, timeout=None):
        for proc in self._procs:
            proc.wait( timeout=timeout.remaining() if timeout is not None else None )

    def close(self):
        self.wait_for_processes()

        for file in self._files:
            file.close()


class ShellCommand(FluidMixin):
    __slots__ = ()

    def create_str(self, cntxt=None):
        if cntxt is None:
            cntxt = ShellCommandCreationContext( 0 )

        return ' '.join( self.create_cmdargs( cntxt ) )

    @abstractmethod
    def create_cmdargs(self, cntxt):
        ...

    def start(self, cntxt=None, stdin=None, stdout=None, stderr=None):
        proc = subprocess.Popen( self.create_cmdargs( cntxt ), stdin=stdin, stdout=stdout, stderr=stderr )

        if cntxt is not None:
            cntxt.process_started( proc )

        return proc

    def execute(self, cntxt=None, stdin=None, stdout=None, stderr=None, timeout=None):
        with self.start( cntxt, stdin, stdout, stderr ) as proc:
            try:
                return proc.wait( timeout=timeout )
            except:
                proc.kill()
                proc.wait()
                raise

    def call(self, cntxt=None, timeout=None):
        with subprocess.Popen( self.create_cmdargs( cntxt ), stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True ) as proc:
            return proc.communicate( timeout=timeout )

    def filtered_call(self, filter_, cntxt=None):
        if filter_ is None:
            filter_ = lambda x: (True, x)
        fout = []

        with subprocess.Popen( self.create_cmdargs( cntxt ), stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True ) as proc:
            for line in proc.stdout:
                incl, obj = filter_( line )
                if incl: fout.append( obj )

            retcode = proc.poll()
            if retcode:
                raise CalledProcessError( retcode, proc.args )

        return fout

    def __or__(self, other):
        return Pipe( self, other )

    def __and__(self, other):
        return Concat( self, other )

    def __gt__(self, other):
        return other( self )

    def __str__(self):
        return self.create_str()

    @classmethod
    def _str(cls, args):
        return [str( a ) for a in args]


class ShellCommandPair(ShellCommand):
    __slots__ = 'first', 'second'

    def __init__(self, first, second):
        super().__init__()

        self.first  = first
        self.second = second

    @property
    @abstractmethod
    def operator(self):
        ...

    def create_cmdargs(self, cntxt):
        args = []

        args.extend( self.first.create_cmdargs( cntxt ) )
        args.append( self.operator if cntxt is None else cntxt.mask_operator( self.operator ) )
        args.extend( self.second.create_cmdargs( cntxt ) )

        return args


class Pipe(ShellCommandPair):
    __slots__ = ()

    @property
    def operator(self):
        return '|'

    def start(self, cntxt=None, stdin=None, stdout=None, stderr=None):
        sproc = self.first.start( cntxt, stdin=stdin, stdout=subprocess.PIPE )
        return self.second.start( cntxt, stdin=sproc.stdout, stdout=stdout, stderr=stderr )


class Concat(ShellCommandPair):
    __slots__ = ()

    @property
    def operator(self):
        return ';'

    def start(self, cntxt=None, stdin=None, stdout=None, stderr=None):
        self.first.start( cntxt, stdin=stdin )
        return self.second.start( cntxt, stdout=stdout, stderr=stderr )


class StdErrToStdOut(ShellCommand):
    __slots__ = '_cmd'

    def __init__(self, cmd):
        self._cmd = cmd

    def create_cmdargs(self, cntxt):
        assert cntxt.within_shell

        args = []

        args.extend( self._cmd.create_cmdargs( cntxt ) )
        args.append( '2' + cntxt.mask_operator( '>&' ) + '1' )

        return args

    def start(self, cntxt=None, stdin=None, stdout=None, stderr=None):
        assert stderr is None
        return self._cmd.start( cntxt, stdin=stdin, stdout=stdout, stderr=subprocess.STDOUT )


class ToFile(ShellCommand):
    __slots__ = '_cmd', '_path'

    def __init__(self, path):
        self._cmd  = None
        self._path = path

    def __call__(self, cmd):
        self._cmd = cmd
        return self

    def create_cmdargs(self, cntxt):
        assert cntxt.within_shell

        args = []

        args.extend( self._cmd.create_cmdargs( cntxt ) )
        args.extend( (cntxt.mask_operator( '>' ), str( self._path ) ) )
        args.append( '2' + cntxt.mask_operator( '>&' ) + '1' )

        return args

    def start(self, cntxt=None, stdin=None, stdout=None, stderr=None):
        assert stdout is None and stderr is None
        file = self._path.open( 'w' )

        if cntxt is not None:
            cntxt.file_opened( file )

        return self._cmd.start( cntxt, stdin=stdin, stdout=file, stderr=subprocess.STDOUT )


class FixedShellCommand(ShellCommand):
    __slots__ = '_args'

    def __init__(self, args):
        super().__init__()
        self._args = args

    def create_cmdargs(self, cntxt):
        return self._str( self._args )


class SingleShellCommand(ShellCommand):
    def __init__(self, cmdpath):
        super().__init__()

        self._cmdpath = Path( cmdpath ) if isinstance( cmdpath, str ) else cmdpath

    def get_cmdname(self):
        return self._cmdpath.name

    def get_cmdpath(self):
        return self._cmdpath


class GenericShellCommand(SingleShellCommand):
    def __init__(self, cmdpath, *args):
        super().__init__( cmdpath )
        self._args = [cmdpath]
        self._args.extend( args )

    def argument(self, *arg):
        return self.extend_arguments( arg )

    def extend_arguments(self, args):
        self._args.extend( args )
        return self

    def create_cmdargs(self, cntxt):
        return self._str( self._args )


class ShellCommandList(ShellCommand):
    def __init__(self):
        super().__init__()

        self._cmds = []

    def command(self, *cmd):
        return self.extend_commands( cmd )

    def extend_commands(self, cmds):
        self._cmds.extend( cmds )
        return self

    def get_commands(self):
        return self._cmds

    def create_cmdargs(self, cntxt):
        args = []

        for cmd in self._cmds:
            if args:
                args.append( ';' if cntxt is None else cntxt.mask_operator( ';' ) )
            args.extend( cmd.create_cmdargs( cntxt ) )

        return args
