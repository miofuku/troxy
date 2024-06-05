#!/usr/bin/env python3.4

from abc import ABC, abstractmethod
from pathlib import Path

from plib.utils import callable_singleton
from translt import Mapping
from translt.streams import StreamMapping


class FileMapping(Mapping):
    @abstractmethod
    def read_from(self, path):
        ...

    @abstractmethod
    def read_object_from(self, path, obj=None):
        ...

    @abstractmethod
    def write_to(self, path, objs):
        ...

    @abstractmethod
    def write_object_to(self, path, obj):
        ...

    @classmethod
    def keyvalue(cls, field, recmapfmt=None, env=None, filefmt=None, **datfmtargs):
        return cls._create_objectmapping( StreamMapping.keyvalue( field, recmapfmt, env, **datfmtargs ), filefmt )

    @classmethod
    def csv(cls, field, recmapfmt=None, env=None, filefmt=None, **datfmtargs):
        return cls._create_objectmapping( StreamMapping.csv( field, recmapfmt, env, **datfmtargs ), filefmt )

    @classmethod
    def json(cls, field, env=None, filefmt=None, **datfmtargs):
        return cls._create_objectmapping( StreamMapping.json( field, env, **datfmtargs ), filefmt )

    @classmethod
    def yaml(cls, field, env=None, filefmt=None, **datfmtargs):
        return cls._create_objectmapping( field, StreamMapping.yaml( field, env, **datfmtargs ), filefmt )

    @classmethod
    def _create_objectmapping(cls, streammap, filefmt ):
        return (filefmt or FileFormat()).mapping_for_stream( streammap )


class FileFormat:
    def __init__(self, newline='', encoding='utf-8'):
        self.newline  = newline
        self.encoding = encoding

    def mapping_for_stream(self, streammap):
        return StreamToFile( streammap, newline=self.newline, encoding=self.encoding )


class PathProcessor(ABC):
    @abstractmethod
    def __call__(self, path):
        ...

    Empty  = None
    IsFile = None

PathProcessor.Empty  = callable_singleton( 'Empty', PathProcessor, lambda self, path: path )
PathProcessor.IsFile = \
    callable_singleton( 'IsFile', PathProcessor, lambda self, path: path if path.is_file() else None )



class FileMappingBase(FileMapping):
    def __init__(self, read_proc=None, write_proc=None, on_error=None, newline='', encoding='utf-8'):
        self.read_proc  = read_proc  or PathProcessor.Empty
        self.write_proc = write_proc or PathProcessor.Empty
        self.on_error   = on_error
        self.newline    = newline
        self.encoding   = encoding

    def read_from(self, path):
        return self._do_read_from( path, self._read_from_stream )

    def read_object_from(self, path, obj=None):
        return self._do_read_from( path, lambda s: self._read_object_from_stream( s, obj ) )

    def _open(self, path, mode):
        po = Path( path ) if isinstance( path, str ) else path
        return po.open( mode, newline=self.newline, encoding=self.encoding )

    def _do_read_from(self, path, func):
        try:
            path = self.read_proc( path )

            if path is None:
                return None
            else:
                with self._open( path, 'r' ) as stream:
                    return func( stream )
        except Exception as err:
            self._on_error( err, path )
            return None

    @abstractmethod
    def _read_from_stream(self, stream):
        ...

    @abstractmethod
    def _read_object_from_stream(self, stream, obj):
        ...

    def write_to(self, path, objs):
        try:
            path = self.write_proc( path )

            if path is not None:
                with self._open( path, 'w' ) as stream:
                    self._write_to_stream( stream, objs )
        except Exception as err:
            self._on_error( err, path )

    def write_object_to(self, path, obj):
        self.write_to( path, (obj,) )

    @abstractmethod
    def _write_to_stream(self, stream, objs):
        ...

    def _on_error(self, error, path):
        if self.on_error is not None:
            self.on_error( error, path )
        else:
            raise error



class StreamToFile(FileMappingBase):
    def __init__(self, strmap, read_proc=None, write_proc=None, on_error=None, newline='', encoding='utf-8'):
        super().__init__( read_proc, write_proc, on_error, newline, encoding )

        self.stream_mapping = strmap

    def _read_from_stream(self, stream):
        return self.stream_mapping.read_from( stream )

    def _read_object_from_stream(self, stream, obj):
        return self.stream_mapping.read_object_from( stream, obj )

    def _write_to_stream(self, stream, objs):
        self.stream_mapping.write_to( stream, objs )



class FileInputProcessor(FileMapping):
    def write_to(self, path, objs):
        raise NotImplementedError()

    def write_object_to(self, path, obj):
        raise NotImplementedError()

    def _write_to_stream(self, stream, objs):
        raise NotImplementedError()
