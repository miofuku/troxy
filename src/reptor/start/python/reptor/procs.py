#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
import subprocess
import time

from plib.shell import ShellCommandContext
from plib.shell.tmux import Tmux
import psutil
from reptor.shell import Bash


# TODO: Composition over inheritence.
class ShellProcess(metaclass=ABCMeta):
    def __init__(self):
        self._execcntxt       = None
        self.concrete_process = None

    @property
    @abstractmethod
    def name(self):
        ...

    @abstractmethod
    def create_command(self):
        ...

    def start(self, cmddec=None):
        self._execcntxt = ShellCommandContext()

        cmd = self.create_command()
        if cmddec is not None:
            cmd = cmddec( cmd )

        self.concrete_process = cmd.start( self._execcntxt )

    def join(self, timeout):
        try:
            self.concrete_process.wait( timeout=timeout.remaining() )
        except psutil.TimeoutExpired as e:
            raise subprocess.TimeoutExpired( e, timeout.seconds )

    def kill(self):
        if self._execcntxt is not None:
            self._execcntxt.kill_processes()
            self._execcntxt.close()
            self._execcntxt = self.concrete_process = None


class JavaProcess(ShellProcess):
    pass



def start_procs_in_background(procs):
    for proc in procs:
        proc.start( Bash )


def start_procs_in_tmux_windows(procs):
    # TODO: shellenv.cmds.create_tmux_command()
    tmux = Tmux()

    for proc in procs:
        tmux.new_window( proc.name, shellcmd=proc.create_command() )

    tmux.execute()


def start_procs_in_tmux_panes(wndname, procs):
    wndready = False
    wndwait  = 3;
    wndpause = 1 / wndwait

    for i, proc in enumerate( procs ):
        cmd = proc.create_command()

        # TODO: Introduce pane layouts for multiple panes (see splitshell)
        if not i:
            cmd = Tmux().new_window( wndname, panename=proc.name, shellcmd=cmd )
        else:
            while not wndready and wndwait:
                wndready = Tmux().list_windownames().filtered_call( lambda line: (line.rstrip()==wndname, True) )

                if not wndready:
                    time.sleep( wndpause )
                    wndwait -= 1

            cmd = Tmux().init_main_window().split_window( panename=proc.name, shellcmd=cmd, target=wndname )

        cmd.execute()
