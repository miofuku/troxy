#!/usr/bin/env python3.4

import os

from montr import ProcessRecorder, ProcessMonitoringPoint, ProcessCPUMonitoringPoint, \
    ProcessMemoryMonitoringPoint, HostMonitoringPoint, HostCoreMonitoringPoint, HostCPUMonitoringPoint, \
    HostNetMonitoringPoint, HostNICMonitoringPoint
from plib.utils.builders import Reference
from translt.data import CSV
from translt.fields import DateTimeField, FloatField, IntegerField, DirectContainerField, FieldMetaType
from translt.fieldsets import RecordFieldSet
from translt.flat import PatternedFlatDictionary


def save_results(recorders, resdir, filename_pattern):
    datfmt = CSV()

    for rec in recorders:
        if rec.results:
            filename = filename_pattern.format( rectype=rec.type, recname=rec.name )

            with ( resdir / filename ).open( 'wt', encoding='utf-8' ) as resfile:
                if isinstance( rec, ProcessRecorder ):
                    strmap = FlatProcessResourcesStreamMapping( datfmt )
                else:
                    strmap = FlatHostResourcesStreamMapping( datfmt )

                strmap.write_object_to( resfile, rec )


class ResourceFileHeader:
    def __init__(self, typ, name, start_time, start_ts, measure_cnt):
        self.type        = typ
        self.name        = name
        self.start_time  = start_time
        self.start_ts    = start_ts
        self.measure_cnt = measure_cnt


class FlatResourcesStreamMapping:
    def __init__(self, datfmt):
        assert datfmt.is_flat

        self._config = RecordFieldSet()
        self.datfmt  = datfmt

    def write_to(self, stream, objs):
        for recorder in objs:
            self.write_object_to( stream, recorder )

    def write_object_to(self, stream, recorder):
        hedmap = self._header_mapping( recorder )
        self.datfmt.write_object( recorder, stream, hedmap )

        stream.write( '\n' )

        resmap = self._result_mapping( recorder )
        self.datfmt.write( recorder.monitoring_points(), stream, resmap )

    def _header_mapping(self, recorder):
        fld = self._config.create_composite( ResourceFileHeader ) \
                                .start_string( 'type' ).attribute().end() \
                                .start_string( 'name' ).attribute().end() \
                                .init_field( DateTimeField, 'start_time' ).attribute().end() \
                                .start_float( 'start_ts' ).attribute().end() \
                                .start_integer( 'measure_cnt' ).constant( len( recorder.results ) ).end()
        return self.datfmt.record_mapping( fld )

    def _cpu_field(self, typ, recmap, timeref):
        timesfld = self._cpu_times_field( recmap )
        loadfld  = FloatField

        return self._cpu_field_base( typ, timeref, 'times', timesfld, 'load', loadfld, 'load' )

    def _detailed_cpu_field(self, typ, recmap, timeref, loadnam='{}', timesnam='{}', offset=0):
        loadsfld = self._config.create_namedsequence().elements_namepattern( loadnam ).indexoffset( offset ).float()
        timesfld = self._config.create_namedsequence().elements_namepattern( timesnam ).indexoffset( offset ) \
                                    .add_field( self._cpu_times_field( recmap ) )

        return self._cpu_field_base( typ,
                    timeref, 'detailed_times', timesfld, 'detailed_loads', loadsfld, None )


    def _cpu_times_field(self, recmap):
        timesfld = self._namedtuple_field( recmap, FloatField )
        timesfld.start_float( 'load' ).no_output()

        return timesfld

    def _cpu_field_base(self, typ, timeref, timesidx, timesfld, loadidx, loadfld, loadnam=None):
        fld = self._config.create_composite( typ ) \
                                .init_field( timesfld, timesidx ).integrate().attribute().end() \
                                .init_field( loadfld, loadidx ).name( loadnam ).attribute( loadidx ).end()

        return fld

    def _namedtuple_field(self, recmap, itemfld=IntegerField, pattern='(.*)'):
        tuplefld = self._config.create_genericnamedtuple().add_field( itemfld )
        recmap[ tuplefld ] = PatternedFlatDictionary( pattern )

        return tuplefld

    def _unbound_dict_field(self, recmap, itemfld, pattern='(.*)', out_key_proc=None):
        dictfld  = self._config.create_dictionary().add_field( itemfld ).output_keyprocessor( out_key_proc )
        recmap[ dictfld ] = PatternedFlatDictionary( pattern )

        return dictfld


class FlatProcessResourcesStreamMapping(FlatResourcesStreamMapping):
    def _header_mapping(self, recorder):
        hedmap = super()._header_mapping( recorder )

        hedmap.field.start_string( '__0__' ).constant( None ).end() \
                    .start_string( '__1__' ).constant( None ).end()

        hedmap.field.start_dictionary( 'thread' ).attribute( 'thread_names' ).string().output_keyprocessor( sorted )

        return hedmap

    def _result_mapping(self, recorder):
        timeref = Reference()

        fld = self._config.create_composite( ProcessMonitoringPoint ) \
                                .start_float( 'timestamp' ).attribute().store( timeref ).end()

        recmap = self.datfmt.record_mapping( fld )

        cpufld = self._cpu_field( ProcessCPUMonitoringPoint, recmap, timeref )
        fld.init_field( cpufld, 'cpu' ).attribute()

        memfld = self._mem_field( recmap, timeref )
        fld.init_field( memfld, 'mem' ).attribute()

        if recorder.perthread:
            threadsfld = self._threads_field( recmap, timeref )
            fld.init_field( threadsfld, 'thread' ).attribute( 'threads' )

        return recmap

    def _mem_field(self, recmap, timeref):
        return self._config.create_composite( ProcessMemoryMonitoringPoint ) \
                        .init_field( self._namedtuple_field( recmap ), 'values' ).attribute().integrate().end()

    class ThreadsField(DirectContainerField):
        metatype = FieldMetaType.dictionary

        def output_subfields(self, cntxt, threads):
            for t, l in zip( threads.detailed_times, threads.detailed_loads ):
                yield None, '{}_cpu_load'.format( t.id ), FloatField, l
            for t in threads.detailed_times:
                yield None, '{}_cpu_user'.format( t.id ), FloatField, t.user_time
                yield None, '{}_cpu_system'.format( t.id ), FloatField, t.system_time

    def _threads_field(self, recmap, timeref):
#        threadsfld = cls._detailed_cpu_field( ProcessThreadMonitoringPoint, recmap, timeref, '{}_cpu_load', '{}_cpu', 1 )
#        timesfld   = threadsfld[ 'detailed_times' ].nested_field.shared_element.field
#        timesfld.init_item( 'id', FieldElement.Disabled )
#        timesfld.init_item( 'user_time', fldnam='user' )
#        timesfld.init_item( 'system_time', fldnam='system' )
#
#        return threadsfld
        return self.ThreadsField( self._config )


class FlatHostResourcesStreamMapping(FlatResourcesStreamMapping):
    def _header_mapping(self, recorder):
        hedmap = super()._header_mapping( recorder )

        hedmap.field.start_integer( 'cpu_count' ).constant( os.cpu_count() )

        return hedmap

    def _result_mapping(self, recorder):
        timeref = Reference()

        fld = self._config.create_composite( HostMonitoringPoint ) \
                                .start_float( 'timestamp' ).attribute().store( timeref ).end()

        recmap = self.datfmt.record_mapping( fld )

        if recorder.percpu:
            cpufld = self._detailed_cpu_field( HostCoreMonitoringPoint, recmap, timeref, '{}_load' )
        else:
            cpufld = self._cpu_field( HostCPUMonitoringPoint, recmap, timeref )

        fld.init_field( cpufld, 'cpu' ).attribute()

        if recorder.pernic:
            netfld = self._nic_field( recmap, timeref )
        else:
            netfld = self._net_field( recmap, timeref )

        fld.init_field( netfld, 'net' ).attribute()

        return recmap

    def _net_field(self, recmap, timeref):
        cntrsfld = self._nic_counters_field( recmap )

        return self._net_field_base( HostNetMonitoringPoint, timeref, 'counters', cntrsfld )

    def _nic_field(self, recmap, timeref):
        nicsfld  = self._unbound_dict_field( recmap, self._nic_counters_field( recmap ), '(.*?)_', out_key_proc=sorted )

        return self._net_field_base( HostNICMonitoringPoint, timeref, 'nic_counters', nicsfld )

    def _net_field_base(self, typ, timeref, countersidx, countersfld):
        return self._config.create_composite( typ ) \
                                .init_field( countersfld, countersidx ).attribute().integrate().end()

    def _nic_counters_field(self, recmap):
        return self._namedtuple_field( recmap )
