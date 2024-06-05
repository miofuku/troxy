#!/usr/bin/env python3.4

from abc import abstractmethod

from translt import Mapping
from translt.data import KeyValueFormat, CSV, JSON, YAML
from translt.flat import FlatFormat
from translt.hier import HierFormat


class StreamMapping(Mapping):
    __slots__ = ()

    @abstractmethod
    def read_from(self, stream):
        ...

    @abstractmethod
    def read_object_from(self, stream, obj=None):
        ...

    @abstractmethod
    def write_to(self, stream, objs):
        ...

    @abstractmethod
    def write_object_to(self, stream, obj):
        ...

    @classmethod
    def keyvalue(cls, field, recmapfmt=None, env=None, **datfmtargs):
        return cls._create_objectmapping( field, KeyValueFormat( **datfmtargs ), recmapfmt or FlatFormat(), env )

    @classmethod
    def csv(cls, field, recmapfmt=None, env=None, **datfmtargs):
        return cls._create_objectmapping( field, CSV( **datfmtargs ), recmapfmt or FlatFormat(), env )

    @classmethod
    def json(cls, field, env=None, **datfmtargs):
        return cls._create_objectmapping( field, JSON( **datfmtargs ), HierFormat(), env )

    @classmethod
    def yaml(cls, field, env=None, **datfmtargs):
        return cls._create_objectmapping( field, YAML( **datfmtargs ), HierFormat(), env )

    @classmethod
    def _create_objectmapping(cls, field, datfmt, recmapfmt, env ):
        return ObjectStreamMapping.for_field( field, datfmt, recmapfmt, env)


class ObjectStreamFormat:
    def __init__(self, datfmt, recmapfmt):
        self.data_format = datfmt
        self.record_mapping_format = recmapfmt

    @classmethod
    def keyvalue(cls, recmapfmt=None, **datfmtargs):
        return cls( KeyValueFormat( **datfmtargs ), recmapfmt or FlatFormat() )

    @classmethod
    def csv(cls, recmapfmt=None, **datfmtargs):
        return cls( CSV( **datfmtargs ), recmapfmt or FlatFormat() )

    @classmethod
    def json(cls, **datfmtargs):
        return cls( JSON( **datfmtargs ), HierFormat() )

    @classmethod
    def yaml(cls, **datfmtargs):
        return cls( YAML( **datfmtargs ), HierFormat() )

    def object_stream(self, field, env=None):
        return ObjectStreamMapping.for_field( field, self.data_format, self.record_mapping_format, env )


class ObjectStreamMapping(StreamMapping):
    @classmethod
    def for_field(cls, field, datfmt, recmapfmt, env=None):
        return cls( datfmt, recmapfmt.record_mapping( field, env ) )

    def __init__(self, datfmt, recmap):
        self.data_format    = datfmt
        self.record_mapping = recmap

    def read_from(self, stream):
        return self.data_format.read( stream, self.record_mapping )

    def read_object_from(self, stream, obj=None):
        return self.data_format.read_object( stream, self.record_mapping, obj )

    def write_to(self, stream, objs):
        self.data_format.write( objs, stream, self.record_mapping )

    def write_object_to(self, stream, obj):
        self.data_format.write_object( obj, stream, self.record_mapping )


class PlainStreamMapping(StreamMapping):
    @classmethod
    def read_from(cls, stream):
        return [cls.read_object_from( stream )]

    @classmethod
    def read_object_from(cls, stream, obj=None):
        if obj is not None:
            raise NotImplementedError( 'Directly reading to objects is not supported.' )

        return stream.read()

    @classmethod
    def write_to(cls, stream, objs):
        for obj in objs:
            cls.write_object_to( stream, obj )

    @classmethod
    def write_object_to(cls, stream, obj):
            stream.write( obj )
