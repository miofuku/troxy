#!/usr/bin/env python3.4

from collections import namedtuple
from enum import Enum
from pathlib import Path
import re

from plib.shell import SingleShellCommand


class JavaCommands:
    def __init__(self, javabin=None):
        self.init_java_cmd_paths( javabin )

    def init_java_command_paths(self, javabin):
        if javabin is None:
            javabin = Path()

        self.java_path   = javabin / 'java'
        self.jps_path    = javabin / 'jps'
        self.jstack_path = javabin / 'jstack'

    def create_java_command(self):
        return Java( cmdpath=self.java_path )

    def create_jps_command(self):
        return JPS( cmdpath=self.jps_path )

    def create_jstack_command(self):
        return JStack( cmdpath=self.jstack_path )


class Java(SingleShellCommand):
    def __init__(self, cmdpath='java'):
        super().__init__( cmdpath )

        self._clspat    = []
        self._opts      = []
        self._clsnam    = None
        self._args      = []
        self._heapmax   = None
        self._heapstart = None
        self._heapyoung = None
        self._useserver = False
        self._asserts   = False

    def classpath(self, *path):
        return self.extend_classpaths( path )

    def extend_classpaths(self, paths):
        self._clspat.extend( paths )
        return self

    def get_classpaths(self):
        return self._clspat

    def use_servervm(self, useserver):
        self._useserver = useserver
        return self

    def get_use_servervm(self):
        return self._useserver

    def maximum_heapsize(self, heapmax):
        self._heapmax = heapmax
        return self

    def get_maximum_heapsize(self):
        return self._heapmax

    def start_heapsize(self, heapstart):
        self._heapstart = heapstart
        return self

    def get_start_heapsize(self):
        return self._heapstart

    def young_heapsize(self, heapyoung):
        self._heapyoung = heapyoung
        return self

    def get_young_heapsize(self):
        return self._heapyoung

    def enable_asserts(self, asserts=True):
        self._asserts = asserts;
        return self

    def get_enable_asserts(self):
        return self._asserts

    def option(self, *opt):
        return self.extend_options( opt )

    def extend_options(self, opts):
        self._opts.extend( opts )
        return self

    def classname(self, clsnam):
        self._clsnam = clsnam
        return self

    def get_classname(self):
        return self._clsnam

    def argument(self, *arg):
        return self.extend_arguments( arg )

    def extend_arguments(self, args):
        self._args.extend( args )
        return self

    def create_cmdargs(self, cntxt):
        args = [self.get_cmdpath()]

        if self._clspat:
            quote = '"' if cntxt is None else cntxt.mask_operator( '"' )
            args.append( '-cp' )
            args.append( quote + ':'.join( [ str( p ) for p in self._clspat] ) + quote )

        if self._useserver:
            args.append( '-server' )

        if self._heapmax is not None:
            args.append( '-Xmx' + str( self._heapmax ) )
        if self._heapstart is not None:
            args.append( '-Xms' + str( self._heapstart ) )
        if self._heapyoung is not None:
            args.append( '-Xmn' + str( self._heapyoung ) )

        if self._asserts:
            args.append( '-ea' )

        if self._opts:
            args.extend( self._opts )

        if self._clsnam is not None:
            args.append( self._clsnam )

        if self._args:
            args.extend( self._args )

        return self._str( args )


class JavaThreadClass(Enum):
    gc        = re.compile( 'GC task thread#\d+' )
    jit       = re.compile( 'C\d+ CompilerThread\d+' )
    jvm_other = re.compile( 'VM Thread|Reference Handler|Finalizer|Signal Dispatcher|Service Thread|VM Periodic Task Thread|Attach Listener' )
    app       = re.compile( '' )

    @classmethod
    def for_name(cls, name):
        for c in cls:
            if c.value.match( name ):
                return c
        else:
            return None


class JavaProcessInfo(namedtuple( 'JavaProcessInfo', 'pid name' )):
    __slots__ = ()


class JavaThreadInfo(namedtuple( 'JavaThreadInfo', 'pid name thread_class' )):
    __slots__ = ()

    @classmethod
    def with_class(cls, pid, name):
        return cls( pid, name, JavaThreadClass.for_name( name ) )

    def get_class(self):
        return JavaThreadClass.for_name( self.name )

    def __str__(self):
        return "JavaThread({}, '{}', {})".format( self.pid, self.name, self.thread_class.name )


class JPS(SingleShellCommand):
    def __init__(self, cmdpath='jps'):
        super().__init__( cmdpath )

    def create_cmdargs(self, cntxt):
        return [self.get_cmdpath()]

    _rejps = re.compile( r'\s*(?P<pid>\d+)\s+(?!Jps)(?P<name>.+)\n' )

    @classmethod
    def _filter_jps(cls, line):
        m = cls._rejps.fullmatch( line )
        return (True, m.groupdict()) if m else (False, None)

    @classmethod
    def javaprocesses(cls):
        pis = {}

        for jproc in cls().filtered_call( cls._filter_jps ):
            pi = JavaProcessInfo( int( jproc[ 'pid' ] ), jproc[ 'name' ] )
            pis[ pi.pid ] = pi

        return pis


class JStack(SingleShellCommand):
    def __init__(self, pid=None, cmdpath='jstack'):
        super().__init__( cmdpath )

        self._pid = pid

    def pid(self, value):
        self._pid = value
        return self

    def get_pid(self):
        return self._pid

    def create_cmdargs(self, cntxt):
        return self._str( [self.get_cmdpath(), self._pid] )

    _rejstack = re.compile( r'"(?P<name>.*?)".+?nid=(?P<tid>0x[\da-f]+)' )

    @classmethod
    def _filter_jstack(cls, line):
        m = cls._rejstack.match( line )
        return (True, m.groupdict()) if m else (False, None)

    @classmethod
    def javathreads(cls, jvmpid):
        pis = {}

        for jthread in cls( jvmpid ).filtered_call( cls._filter_jstack ):
            pi = JavaThreadInfo( int( jthread['tid'], 16 ), jthread[ 'name' ], None )
            pis[ pi.pid ] = pi

        return pis
