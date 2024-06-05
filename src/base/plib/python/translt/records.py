#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta

from translt.fieldelms import FieldContext
from translt import Mapping, Format


class RecordMappingError(RuntimeError):
    pass


class FieldFormatError(Exception):
    pass


class NotFound(FieldFormatError):
    pass


class EmptyCollection(FieldFormatError):
    pass


class SubfieldError(FieldFormatError):
    def __init__(self, field, index, name, subfldref, suberr):
        self.field     = field
        self.index     = index
        self.name      = name
        self.subfldref = subfldref
        self.suberr    = suberr

    def __str__(self):
        return '{}/{} - {}'.format( self.index, self.name, self.suberr )



class FieldFormat(Format):
    @abstractmethod
    def read(self, cntxt, field):
        ...

    @abstractmethod
    def write(self, obj, cntxt, field):
        ...


class RecordMappingFormat(metaclass=ABCMeta):
    def __init__(self, fieldsep=None):
        self.fieldsep = fieldsep if fieldsep is not None else '_'

    @abstractmethod
    def record_mapping(self, field, env=None):
        ...


# TODO: Externalise context like Intrpter does
class RecordMapping(Mapping):
    @abstractmethod
    def read_from(self, datasrc, obj=None):
        ...

    @abstractmethod
    def write_to(self, datasnk, obj):
        ...



class RecordMappingBase(dict, RecordMapping):
    def __init__(self, field, env, fmt, defaults):
        self.field           = field
        self.env             = env
        self.format          = fmt
        self.default_formats = defaults

    def get_format(self, field):
        if field in self:
            return self[ field ]
        elif field.metatype in self.default_formats:
            return self.default_formats[ field.metatype ]
        else:
            raise RecordMappingError( 'No format specified for {}'.format( field ) )


class MappingContextBase(FieldContext):
    def __init__(self, mapping):
        self.mapping = mapping
        self._name_procs  = []
        self._field_store = {}

    @property
    def name_procs(self):
        return self._name_procs

    @property
    def field_store(self):
        return self._field_store

    @property
    def env(self):
        return self.mapping.env


# TODO: Combine read and write to process with direction?
class ReadContextMixin:
    def read(self, field, obj):
        fmt = self.mapping.get_format( field )

        if fmt:
            return fmt.read( self, field, obj )
        else:
            raise NotFound()

    def read_subfield(self, field, index, subfldref, obj):
        subfld, value = field.get_translator().prepare_subfield_input( self, index, subfldref, obj )

        if not subfld:
            return value

        try:
            return field.get_translator().process_subfield_input( self, index, subfldref, self.read( subfld, obj ) )
        finally:
            field.get_translator().finish_subfield_input( self, index, subfldref )



class WriteContextMixin:
    def write(self, obj, field):
        fmt = self.mapping.get_format( field )

        if fmt:
            fmt.write( obj, self, field )
        else:
            raise NotFound()

    def write_subfield(self, field, index, subfldref, obj):
        subfld, value = field.get_translator().prepare_subfield_output( self, index, subfldref, obj )

        if not subfld:
            return

        try:
            self.write( value, subfld )
        finally:
            field.get_translator().finish_subfield_output( self, index, subfldref, obj )
