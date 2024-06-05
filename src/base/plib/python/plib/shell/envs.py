#!/usr/bin/env python3.4

from plib.shell.files import FileCommands
from plib.shell.io import IOCommands
from plib.shell.java import JavaCommands
from plib.shell.ssh import SshCommands
from plib.shell.tmux import TmuxCommands


class ShellCommands(FileCommands, IOCommands, SshCommands, TmuxCommands, JavaCommands):
    def __init__(self):
        FileCommands.__init__( self )
        IOCommands.__init__( self )
        SshCommands.__init__( self )
        TmuxCommands.__init__( self )
        JavaCommands.__init__( self )
