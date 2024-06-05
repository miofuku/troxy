#!/usr/bin/env python3.4

from collections import OrderedDict

from translt.fields import FieldMetaType
from translt.records import RecordMappingBase, ReadContextMixin, WriteContextMixin, FieldFormat, \
    EmptyCollection, NotFound, MappingContextBase, SubfieldError, RecordMappingFormat


class HierScalar(FieldFormat):
    def read(self, cntxt, field, obj):
        assert obj is None

        return field.get_translator().create_object( cntxt, cntxt.current ) if cntxt.current is not None else None

    def write(self, obj, cntxt, field):
        cntxt.data = None if obj is None else field.get_translator().get_value( obj )



class HierSequence(FieldFormat):
    def read(self, cntxt, field, obj):
        if cntxt.current is None:
            return None
        else:
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
                if index==len( cntxt.current ):
                    break
                yield cntxt.read_subfield( field, index, subfldref, cntxt.current[ index ], None )
            except (NotFound, EmptyCollection) as e:
                raise e
            except Exception as e:
                raise SubfieldError( field, index, index, subfldref, e )

    def _update_items(self, cntxt, field, obj):
        for index, _, subfldref, value in field.get_translator().updating_subfields( cntxt, obj ):
            try:
                cntxt.read_subfield( field, index, subfldref, cntxt.current[ index ], value )
            except Exception as e:
                raise SubfieldError( field, index, index, subfldref, e )


    def write(self, obj, cntxt, field):
        if obj is None:
            cntxt.data = None
        else:
            cntxt.data = []
            field.get_translator().prepare_output( cntxt, obj )

            try:
                for index, _, subfldref, value in field.get_translator().output_subfields( cntxt, obj ):
                    try:
                        cntxt.data.append( cntxt.write_subfield( field, index, subfldref, value ) )
                    except (NotFound, EmptyCollection) as e:
                        raise e
                    except Exception as e:
                        raise SubfieldError( field, index, index, subfldref, e )

            finally:
                field.get_translator().finish_output( cntxt, obj )


class HierDictionary(FieldFormat):
    def read(self, cntxt, field, obj):
        if cntxt.current is None:
            return None
        else:
            avail_names = cntxt.current.keys() if field.get_translator().prepare_input( cntxt ) else None

            try:
                obj = field.get_translator().process_object( cntxt, obj, self._read_items( cntxt, field, obj, avail_names ) )

                self._update_items( cntxt, field, obj )

                return obj
            finally:
                field.get_translator().finish_input( cntxt )

    def _get_data_source(self, cntxt, name):
        if name is None:
            return cntxt.current
        elif name in cntxt.current:
            return cntxt.current[ name ]
        else:
            return None

    def _read_items(self, cntxt, field, obj, avail_names):
        for index, name, subfldref in field.get_translator().creating_subfields( cntxt, obj, avail_names ):
            datasrc = self._get_data_source( cntxt, name )
            if datasrc is None:
                if not field.get_translator().is_finite:
                    break
                else:
                    continue

            try:
                yield index, cntxt.read_subfield( field, index, subfldref, datasrc, None )
            except NotFound:
                if not field.get_translator().is_finite:
                    break
            except EmptyCollection as e:
                raise e
            except Exception as e:
                raise SubfieldError( field, index, name, subfldref, e )

    def _update_items(self, cntxt, field, obj):
        for index, name, subfldref, value in field.get_translator().updating_subfields( cntxt, obj ):
            datasrc = self._get_data_source( cntxt, name )
            if datasrc is None:
                if not field.get_translator().is_finite:
                    break
                else:
                    continue

            try:
                cntxt.read_subfield( field, index, subfldref, datasrc, value )
            except NotFound:
                pass
            except EmptyCollection as e:
                raise e
            except Exception as e:
                raise SubfieldError( field, index, name, subfldref, e )


    def write(self, obj, cntxt, field):
        if obj is None:
            cntxt.data = None
        else:
            cntxt.data = OrderedDict()
            field.get_translator().prepare_output( cntxt, obj )

            try:
                for index, name, subfldref, value in field.get_translator().output_subfields( cntxt, obj ):
                    try:
                        if name is None:
                            subdata = cntxt.write_subfield( field, index, subfldref, value )
                            if subdata is not None:
                                cntxt.data.update( subdata )
                        else:
                            cntxt.data[ name ] = cntxt.write_subfield( field, index, subfldref, value )
                    except NotFound:
                        pass
                    except EmptyCollection as e:
                        raise e
                    except Exception as e:
                        raise SubfieldError( field, index, name, subfldref, e )
            finally:
                field.get_translator().finish_output( cntxt, obj )


class HierFormat(RecordMappingFormat):
    @classmethod
    def record_mapping(cls, field, env=None):
        return HierMapping( field, env )


class HierMapping(RecordMappingBase):
    default_format = HierFormat()

    def __init__(self, field, env=None, fmt=None):
        if fmt is None:
            fmt = self.default_format

        defaults = {}
        defaults[ FieldMetaType.int ]        = HierScalar()
        defaults[ FieldMetaType.float ]      = HierScalar()
        defaults[ FieldMetaType.string ]     = HierScalar()
        defaults[ FieldMetaType.bool ]       = HierScalar()
        defaults[ FieldMetaType.sequence ]   = HierSequence()
        defaults[ FieldMetaType.dictionary ] = HierDictionary()

        super().__init__( field, env, fmt, defaults )

    def read_from(self, datasrc, obj=None):
        cntxt = HierReadContext( self, datasrc )

        return cntxt.read( self.field, obj )

    def write_to(self, datasnk, obj):
        cntxt = HierWriteContext( self )

        cntxt.write( obj, self.field )

        datasnk.append( cntxt.data )


class HierContext(MappingContextBase):
    pass


class HierReadContext(HierContext, ReadContextMixin):
    def __init__(self, mapping, datasrc):
        super().__init__( mapping )

        self.current = datasrc

    def read_subfield(self, field, index, subfldref, datasrc, obj):
        save, self.current = self.current, datasrc

        try:
            return super().read_subfield( field, index, subfldref, obj )
        finally:
            self.current = save


class HierWriteContext(HierContext, WriteContextMixin):
    def __init__(self, mapping):
        super().__init__( mapping )

        self.data = None

    def write_subfield(self, field, index, subfldref, obj):
        save, self.data = self.data, None

        try:
            super().write_subfield( field, index, subfldref, obj )
        finally:
            data, self.data = self.data, save

        return data
