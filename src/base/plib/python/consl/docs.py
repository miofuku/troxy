#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
import sys
from textwrap import TextWrapper


class DocWriter:
    def __init__(self, stream=sys.stdout, tabwidth=4, linewidth=70):
        self.stream   = stream
        self._wrapper = TextWrapper( expand_tabs=tabwidth, width=linewidth )
        self._indent  = 0

    def newline(self):
        print( '', file=self.stream )

    def write_block(self, text):
        print( self._wrapper.fill( text ), file=self.stream )

    @property
    def indentation(self):
        return self._indent

    @indentation.setter
    def indentation(self, value):
        self._indent = value
        self._init_indent()

    def indent(self, count=1):
        self._indent += count
        self._init_indent()

    def _init_indent(self):
        self._wrapper.initial_indent = self._wrapper.subsequent_indent = \
                ' ' * ( self.indentation*self.tabwidth )
    @property
    def tabwidth(self):
        return self._wrapper.expand_tabs

    @tabwidth.setter
    def tabwidth(self, value):
        self._wrapper.expand_tabs = value
        self._init_indent()

    @property
    def linewidth(self):
        return self._wrapper.width

    @linewidth.setter
    def linewidth(self, value):
        self._wrapper.width = value



class CommandDoc:
    def __init__(self, name, usage, abstract, description):
        self.name        = name
        self.usage       = usage
        self.abstract    = abstract
        self.description = description

        self.parameters  = []
        self.subcommands = []


    def write(self, writer=None):
        if writer is None:
            writer = DocWriter( sys.stdout )

        nametxt = '{} - {}'.format( self.name, self.abstract ) if self.abstract else self.name
        self._write_section( writer, 'Name', nametxt )

        if self.usage:
            self._write_section( writer, 'Usage', self.usage )

        if self.description:
            self._write_section( writer, 'Description', self.description )

        if self.parameters:
            self._start_section( writer, 'Parameters', None )
            for param in self.parameters:
                writer.write_block( param.usage )
                if param.description is not None:
                    writer.indent()
                    writer.write_block( param.description )
                    writer.indent( -1 )
                writer.newline()
            self._end_section( writer, suppress_newline=True )

        if self.subcommands:
            self._start_section( writer, 'Subcommands', None )
            maxname = 0
            for subcmd in self.subcommands:
                maxname = max( maxname, len( subcmd.name )+writer.tabwidth )
            for subcmd in self.subcommands:
                if subcmd.abstract is None:
                    writer.write_block( subcmd.name )
                else:
                    writer.write_block( '{:{}}{}'.format( subcmd.name, maxname, subcmd.abstract ) )
            self._end_section( writer, suppress_newline=True )


    def _write_heading(self, writer, heading):
        writer.write_block( heading + ':' )

    def _write_section(self, writer, heading, text):
        self._start_section( writer, heading, text )
        self._end_section( writer )

    def _start_section(self, writer, heading, text):
        self._write_heading( writer, heading )
        writer.indent()
        if text:
            writer.write_block( text )

    def _end_section(self, writer, suppress_newline=False):
        writer.indent( -1 )
        if not suppress_newline:
            writer.newline()



class ParameterDoc:
    def __init__(self, usage, description):
        self.usage       = usage
        self.description = description



class DocGenerator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def create_doc(self):
        ...
