#!/usr/bin/env python3.4

from abc import ABC, abstractmethod
from pathlib import Path

from translt import Mapping, InputMapping


# DirectoryObjectInputMapping
class DirectoryInputMapping(InputMapping):
    @abstractmethod
    def read_from(self, directory):
        ...


class DirectoryMapping(DirectoryInputMapping, Mapping):
    @abstractmethod
    def write_to(self, directory, objs):
        ...



class DirectoryPatternFormat(ABC):
    @abstractmethod
    def read(self, directory, file_pattern, pattern_kwargs, file_map):
        ...



class DirectoryPatternToDirectory(DirectoryInputMapping):
    def __init__(self, pattern_reader, file_pattern, file_reader):
        self.pattern_reader = pattern_reader
        self.file_pattern   = file_pattern
        self.file_reader    = file_reader

    def read_from(self, directory):
        return self.pattern_reader.read( directory, self.file_pattern, {}, self.file_reader )



class FileToDirectory(DirectoryMapping):
    def __init__(self, file_name, file_map):
        self.file_name = file_name
        self.file_map  = file_map

    def read_from(self, directory):
        return self.file_map.read_from( self._get_path( directory ) )

    def write_to(self, directory, objs):
        self.file_map.write_to( self._get_path( directory ), objs )

    def _get_path(self, directory):
        return Path( directory ) / self.file_name



class FileToDirectoryPattern(DirectoryPatternFormat):
    def read(self, directory, file_pattern, pattern_kwargs, file_reader):
        path = Path( directory ) / file_pattern.format( **pattern_kwargs )

        return file_reader.read_from( path )



class DirectoryListFormat(DirectoryPatternFormat):
    def __init__(self, sub_map, start_index=0, index_name='index'):
        self.sub_map     = sub_map
        self.start_index = start_index
        self.index_name  = index_name

    def _read_index(self, directory, file_pattern, pattern_kwargs, index, file_reader):
        pattern_kwargs[ self.index_name ] = index
        return self.sub_map.read( directory, file_pattern, pattern_kwargs, file_reader )



class UnboundFileEnumeration(DirectoryListFormat):
    def read(self, directory, file_pattern, pattern_kwargs, file_reader):
        index = self.start_index
        objs  = []

        while True:
            file_objs = self._read_index( directory, file_pattern, pattern_kwargs, index, file_reader )

            if not file_objs:
                break

            objs.extend( file_objs )
            index += 1

        return objs



class FileEnumeration(DirectoryListFormat):
    def __init__(self, sub_reader, count, start_index=0, index_name='index'):
        super().__init__( sub_reader, start_index, index_name )

        self.count = count

    def read(self, directory, file_pattern, pattern_kwargs, file_reader):
        obj_list = []

        for index in range( self.start_index, self.start_index+self.count ):
            objs = self._read_index( directory, file_pattern, pattern_kwargs, index, file_reader )

            if objs:
                obj_list.extend( objs )

        return obj_list
