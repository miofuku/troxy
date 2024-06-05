#!/usr/bin/env python3.4

from collections import OrderedDict
import re

from translt.fieldelms import PrefixProcessor
from translt.fields import FieldMetaType
from translt.records import RecordMappingBase, WriteContextMixin, ReadContextMixin, FieldFormat, NotFound, \
    EmptyCollection, MappingContextBase, SubfieldError, RecordMappingFormat


class FlatFieldFormat(FieldFormat):
    pass

# TODO: Catching KeyError masks errors not related to translation!

# TODO: Convert scalars like in Hier/YAML!
class FlatScalar(FlatFieldFormat):
    def __init__(self, colname=''):
        self.colname = colname

    def read(self, cntxt, field, obj):
        if obj is not None:
            assert self._get_column_name( cntxt ) not in cntxt.data
            raise KeyError()

        value = cntxt.read_value( self, self._get_column_name( cntxt ) )
        return field.get_translator().create_object( cntxt, value ) if value not in (None, '') else None

    def write(self, obj, cntxt, field):
        valstr = obj if obj is None else self._get_value_string( field, obj )

        cntxt.write_value( field, self._get_column_name( cntxt ), valstr )

    def _get_value_string(self, field, obj):
        return str( field.get_translator().get_value( obj ) )

    def _get_column_name(self, cntxt):
        return cntxt.get_full_column_name( self.colname )


class FlatBoolean(FlatScalar):
    def __init__(self, truestr='True', falsestr='False', colname=''):
        super().__init__( colname )

        self.truestr  = truestr
        self.falsestr = falsestr

    def _get_value_string(self, field, obj):
        return self.truestr if obj else self.falsestr



class Enumeration(FlatFieldFormat):
    def __init__(self, proc=None, start=0):
        self.proc  = proc or PrefixProcessor.PlainIndex
        self.start = start

    def read(self, cntxt, field, obj):
        field.get_translator().prepare_input( cntxt )

        try:
            obj = field.get_translator().process_object( cntxt, obj, self._read_items( cntxt, field, obj ) )

            self._update_items( cntxt, field, obj )

            return obj
        except EmptyCollection:
            return None
        finally:
            field.get_translator().finish_input( cntxt )


    def _read_items(self, cntxt, field, obj):
        for index, _, subfldref in field.get_translator().creating_subfields( cntxt, obj ):
            try:
                yield cntxt.read_subfield( field, index, self.proc.for_index( self.start+index, cntxt ), subfldref, None )
            except (KeyError, NotFound):
                if index==0:
                    raise NotFound()
                else:
                    return
            except EmptyCollection as e:
                raise e
            except Exception as e:
                raise SubfieldError( field, index, index, subfldref, e )

    def _update_items(self, cntxt, field, obj):
        for index, _, subfldref, value in field.get_translator().updating_subfields( cntxt, obj ):
            try:
                cntxt.read_subfield( field, index, self.proc.for_index( self.start+index, cntxt ), subfldref, value )
            except Exception as e:
                raise SubfieldError( field, index, index, subfldref, e )


    def write(self, obj, cntxt, field):
        if obj is None and not cntxt.column_names_only:
            return

        field.get_translator().prepare_output( cntxt, obj )

        try:
            for index, _, subfldref, value in field.get_translator().output_subfields( cntxt, obj ):
                try:
                    cntxt.write_subfield( field, index, self.proc.for_index( self.start+index, cntxt ), subfldref, value )
                except (NotFound, EmptyCollection) as e:
                    raise e
                except Exception as e:
                    raise SubfieldError( field, index, index, subfldref, e )
        finally:
            field.get_translator().finish_output( cntxt, obj )



class FlatDictionary(FlatFieldFormat):
    def __init__(self, proc=None):
        self.proc = proc or PrefixProcessor.PlainIndex


    def read(self, cntxt, field, obj):
        if not cntxt.can_be_contained():
            raise NotFound()

        avail_names = self._search_names( cntxt, field ) if field.get_translator().prepare_input( cntxt ) else None

        try:
            obj = field.get_translator().process_object( cntxt, obj, self._read_items( cntxt, field, obj, avail_names ) )

            self._update_items( cntxt, field, obj, avail_names )

            return obj
        except EmptyCollection:
            return None
        finally:
            field.get_translator().finish_input( cntxt )

    def _read_items(self, cntxt, field, obj, avail_names):
        found = 0

        for index, name, subfldref in field.get_translator().creating_subfields( cntxt, obj, avail_names ):
            proc = self.proc.for_index( name, cntxt ) if name is not None else None
            try:
                yield index, cntxt.read_subfield( field, index, proc, subfldref, None )
                found += 1
            except (KeyError, NotFound):
                if not field.get_translator().is_finite:
                    break
            except EmptyCollection as e:
                raise e
            except Exception as e:
                raise SubfieldError( field, index, name, subfldref, e )

        if found==0 and obj is None:
            raise NotFound()

    def _update_items(self, cntxt, field, obj, avail_names):
        for index, name, subfldref, value in field.get_translator().updating_subfields( cntxt, obj, avail_names ):
            proc = self.proc.for_index( name, cntxt ) if name is not None else None
            try:
                cntxt.read_subfield( field, index, proc, subfldref, value )
            except (KeyError, NotFound):
                pass
            except Exception as e:
                raise SubfieldError( field, index, name, subfldref, e )


    def _search_names(self, cntxt, field):
        return None


    def write(self, obj, cntxt, field):
        if obj is None and not cntxt.column_names_only:
            return

        field.get_translator().prepare_output( cntxt, obj )

        try:
            for index, name, subfldref, value in field.get_translator().output_subfields( cntxt, obj ):
                proc = self.proc.for_index( name, cntxt ) if name is not None else None
                try:
                    cntxt.write_subfield( field, index, proc, subfldref, value )
                except NotFound:
                    pass
                except EmptyCollection as e:
                    raise e
                except Exception as e:
                    raise SubfieldError( field, index, name, subfldref, e )
        finally:
            field.get_translator().finish_output( cntxt, obj )



class PatternedFlatDictionary(FlatDictionary):
    def __init__(self, index_pattern, index_proc=None, proc=None):
        super().__init__( proc )

        self.index_pattern = index_pattern
        self.index_proc    = index_proc

    def _search_names(self, cntxt, field):
        pattern = re.compile( cntxt.get_full_column_name( self.index_pattern ) )
        names   = set()

        for key in cntxt.data.keys():
            m = pattern.match( key )
            if m:
                name = m.group( 1 )
                if self.index_proc:
                    name = self.index_proc( name )
                names.add( name )

        return names



class ColumnNames(list):
    def __init__(self):
        super().__init__()

        self.fields_to_cols = {}

    def add_column(self, colname, field):
        if field in self.fields_to_cols:
            lst = self.fields_to_cols[ field ]
        else:
            lst = self.fields_to_cols.setdefault( field, [] )

        lst.append( colname )
        self.append( colname )

    def add_group(self, cols, field, index):
        if field in self.fields_to_cols:
            groups = self.fields_to_cols[ field ]
        else:
            groups = self.fields_to_cols.setdefault( field, OrderedDict() )

        group = groups[ index ] if index in groups else groups.setdefault( index, [] )
        group.extend( cols )

    def names_only(self):
        return list( self )


class FlatFormat(RecordMappingFormat):
    def __init__(self, fieldsep=None, truestr=None, falsestr=None, data_read_hook=None):
        super().__init__( fieldsep )

        self.truestr  = truestr if truestr is not None else 'True'
        self.falsestr = falsestr if falsestr is not None else 'False'

        self.data_read_hook = data_read_hook

    def record_mapping(self, field, env=None):
        return FlatMapping( field, env, self )


class FlatMapping(RecordMappingBase):
    default_format = FlatFormat()

    def __init__(self, field, env=None, fmt=None):
        if fmt is None:
            fmt = self.default_format

        defaults = {}
        defaults[ FieldMetaType.int ]        = FlatScalar()
        defaults[ FieldMetaType.float ]      = FlatScalar()
        defaults[ FieldMetaType.string ]     = FlatScalar()
        defaults[ FieldMetaType.bool ]       = FlatBoolean( truestr=fmt.truestr, falsestr=fmt.falsestr )
        defaults[ FieldMetaType.sequence ]   = Enumeration()
        defaults[ FieldMetaType.dictionary ] = FlatDictionary()

        super().__init__( field, env, fmt, defaults )

    def read_from(self, datasrc, obj=None):
        cntxt = FlatReadContext( self, datasrc )

        retobj = cntxt.read( self.field, obj )

        if self.format.data_read_hook:
            self.format.data_read_hook( self, datasrc, retobj, obj, cntxt.used_columns )

        return retobj

    def write_to(self, datasnk, obj, column_names=None):
        cntxt = FlatWriteContext( self, datasnk, column_names )

        cntxt.write( obj, self.field )

    def get_column_names(self, obj=None):
        colnames = ColumnNames()

        self.write_to( None, obj, colnames )

        return colnames



class _Sentinal:
    __slots__ = ()
    def __str__(self):
        return ''


class FlatContext(MappingContextBase):
    def __init__(self, mapping, data):
        super().__init__( mapping )

        self.data  = data
        self.procs = []

    def get_full_column_name(self, base_name):
        return self.get_column_name( self.apply_procs( base_name ) )

    __check_sentinal = _Sentinal()

    def can_be_contained(self):
        if not self.procs:
            return True
        else:
            parts   = self.apply_procs( FlatContext.__check_sentinal )
            si      = parts.index( FlatContext.__check_sentinal )
            before  = re.escape( self.get_column_name( parts[ :si ] ) )   if si>0 else ''
            after   = re.escape( self.get_column_name( parts[ si+1: ] ) ) if si<len( parts ) else ''
            pattern = re.compile( before + '.*' + after )

            for colname in self.data.keys():
                if pattern.search( colname ):
                    return True
            else:
                return False

    def apply_procs(self, base):
        parts = [ base ] if base else []
        for proc in self.procs[ ::-1 ]:
            proc( parts )
        return parts

    def get_column_name(self, parts):
        return self.mapping.format.fieldsep.join( parts )


class FlatReadContext(FlatContext, ReadContextMixin):
    def __init__(self, mapping, datasnk):
        super().__init__( mapping, datasnk )

        self.used_columns = None if mapping.format.data_read_hook is None else set()

    def read_value(self, fmt, colname):
        if self.used_columns is not None:
            self.used_columns.add( colname )

        return self.data[ colname ]

    def read_subfield(self, field, index, proc, subfldref, obj):
        if proc is not None:
            self.procs.append( proc )

        try:
            return super().read_subfield( field, index, subfldref, obj )
        finally:
            if proc is not None:
                self.procs.pop()



class FlatWriteContext(FlatContext, WriteContextMixin):
    def __init__(self, mapping, datasnk, column_names=None):
        super().__init__( mapping, datasnk )

        self.column_names = column_names
        self.group_pos    = []

    @property
    def column_names_only(self):
        return self.data is None

    def write_value(self, field, column_name, value):
        if not self.column_names_only:
            self.data[ column_name ] = value

        if self.column_names is not None:
            self.column_names.add_column( column_name, field )

    def write_subfield(self, field, index, proc, subfldref, obj):
        if index is not None:
            self.start_group( field, index )
        if proc is not None:
            self.procs.append( proc )

        try:
            super().write_subfield( field, index, subfldref, obj )
        finally:
            if proc is not None:
                self.procs.pop()
            if index is not None:
                self.end_group( field, index )

    def start_group(self, field, index):
        if self.column_names is not None:
            self.group_pos.append( len( self.column_names ) )

    def end_group(self, field, index):
        if self.column_names is not None:
            start = self.group_pos.pop()
            self.column_names.add_group( self.column_names[ start: ], field, index )
