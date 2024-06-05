#!/usr/bin/env python3.4

from subprocess import CalledProcessError

from montr import ResourceMonitor
from plib.shell.java import JStack
import psutil


# TODO: Rely on process types to determine real processes.
class ReptorResourceMonitor(ResourceMonitor):
    def init_processes(self):
        for proc in psutil.process_iter():
            if proc.name()!='java':
                continue

            cliter = iter( proc.cmdline() )
            curarg = ''
            name   = None

            while curarg is not None and name is None:
                curarg = next( cliter, None )

                if curarg=='reptor.start.ReplicaHost' or curarg=='reptor.smart.SmartReplica':
                    name = 'replica'
                elif curarg=='reptor.start.BenchmarkerHost':
                    name = 'client'

            if name is not None:
                procid = next( cliter, None )

                if procid is not None:
                    threadnames = self._determine_threadnames( proc ) if self.monitor_per_thread else None
                    self.monitor_process( proc, name+procid, threadnames )

        if not self.processes:
            raise RuntimeError( 'No processes found' )

    def _determine_threadnames(self, proc):
        try:
            javathreads = JStack.javathreads( proc.pid ).values()
            return { jthread.pid: jthread.name for jthread in javathreads }
        except CalledProcessError as e:
            print( 'Could not get java thread names: {}'.format( e ) )
            return None
