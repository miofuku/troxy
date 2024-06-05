#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
import csv
import re

from measr import RateResult, FrequencySink, StatisticalAccumulator
from measr.sysres import ThreadCPUTimes, ProcessCPUTimes, ProcessResources, SystemCPUTimes, HostResources, \
    TransmittedPacketsResult
from measr.translt import FrequencyField
from plib.shell.java import JavaThreadClass, JavaThreadInfo
from translt.files import FileInputProcessor, FileMappingBase


# TODO: Should not be confined to java processes
class ResourcesFileMapping(FileInputProcessor, FileMappingBase):
    def _store_values(self, data, stores):
        for s in stores:
            s.store( data )

    def _get_classes(self, fields, pattern):
        cls_set = set()

        for f in fields:
            m = pattern.match( f )

            if m:
                cls_set.add( m.group( 1 ) )

        cls_lst = list( cls_set )
        cls_lst.sort()

        return cls_lst

    def _calc_class_rates(self, cumvals):
        sum_  = 0
        rates = {}

        for v in cumvals:
            sum_ += v.diff
        for v in cumvals:
            rates[ v.name ] = v.diff/sum_ if sum_!=0 else None

        return rates

    def _read_from_stream(self, stream):
            header  = next( csv.DictReader( stream, delimiter=';' ) )

            meascnt = int( header[ 'measure_cnt' ] )

            rcnt = meascnt//10*10
            warmup   = round( 0.6*rcnt )
            cooldown = round( 0.1*rcnt )
            run      = rcnt-warmup-cooldown

            if run<10:
                raise ResultError( 'Unexpected number of measures ({}) in {}'.format( meascnt, stream.name ) )

            stream.readline()

            s = self._generate_summary_from_reader( csv.DictReader( stream, delimiter=';' ), header, meascnt-cooldown-run+5, meascnt-cooldown-5 )

            return [ s ]

    def _read_object_from_stream(self, stream, obj):
        raise NotImplementedError()

    @abstractmethod
    def _generate_summary_from_reader(self, reader, header, start_idx, end_idx):
        ...


class ProcessResourcesFileMapping(ResourcesFileMapping):
    _thread_pattern = re.compile( 'thread_(?P<tid>\d+)')

    def _generate_summary_from_reader(self, reader, header, start_idx, end_idx):
        stores = []

        timestamp = ScalarCumulativeValue( 'timestamp', float )
        stores.append( timestamp )

        cpu_user = ScalarCumulativeValue( 'cpu_user', float )
        stores.append( cpu_user )
        cpu_sys  = ScalarCumulativeValue( 'cpu_system', float )
        stores.append( cpu_sys )

        tcls_cols = { tcls: [] for tcls in JavaThreadClass }
        thread_stores = {}
        for col, val in header.items():
            m = self._thread_pattern.fullmatch( col )

            if m:
                tid  = int( m.group( 'tid' ) )
                tinf = JavaThreadInfo.with_class( tid, val )
                tcls = tinf.thread_class

                for ccls in 'user', 'system':
                    col = 'thread_{}_cpu_{}'.format( tid, ccls )
                    tcls_cols[ tcls ].append( col )
                    sto = ScalarCumulativeValue( col, float )
                    thread_stores.setdefault( tinf, [] ).append( sto )
                    stores.append( sto )

        thread_classes = {}
        for tcls, cols in tcls_cols.items():
            val = SummedCumulativeValue( tcls.name, float, ValueList( cols ) )
            thread_classes[ tcls.name ] = val
            stores.append( val )

        load = StatisticalAccumulator()
        idx  = 0

        for data in reader:
            if idx==start_idx:
                self._store_values( data, stores )
            elif idx==end_idx:
                self._store_values( data, stores )
                mem_rss   = int( data[ 'mem_rss' ] )
                mem_vms   = int( data[ 'mem_vms' ] )

            if start_idx <= idx < end_idx:
                load.accept( float( data[ 'cpu_load' ] ) )

            idx += 1

        secs       = timestamp.diff
        sys_share  = cpu_sys.diff/(cpu_sys.diff + cpu_user.diff) if cpu_sys.diff+cpu_user.diff else None
        tcls_rates = self._calc_class_rates( thread_classes.values() )

        thread_times_s = ThreadCPUTimes()
        thread_times_e = ThreadCPUTimes()
        for tinf, stos in thread_stores.items():
            thread_times_s[ tinf ] = ProcessCPUTimes( stos[ 0 ].start, stos[ 1 ].start )
            thread_times_e[ tinf ] = ProcessCPUTimes( stos[ 0 ].end, stos[ 1 ].end )
        thread_loads = ThreadCPUTimes.calc_load_per_thread( timestamp.start, thread_times_s, timestamp.end, thread_times_e )

        return ProcessResources( RateResult( int( secs*1000000000 ), load ), sys_share, tcls_rates, mem_rss, mem_vms, thread_loads )


CPU_LOAD_FREQ_COUNT = 4
CPU_LOAD_FREQ_BINS  = [ i/CPU_LOAD_FREQ_COUNT for i in range( 1, CPU_LOAD_FREQ_COUNT ) ]

class CPULoadFrequencyField(FrequencyField):
    def __init__(self, config, pattern):
        bin_names = [ pattern.format( (i + 1)*100//CPU_LOAD_FREQ_COUNT ) for i in range( CPU_LOAD_FREQ_COUNT ) ]

        super().__init__( config, bin_names )

    def _get_bins(self, args):
        return CPU_LOAD_FREQ_BINS


class HostResourcesFileMapping(ResourcesFileMapping):
    _pattern_classes_cputime = re.compile( '^cpu_0_(?!load)(.+)$')
    _pattern_classes_net = re.compile( '^net_([^_]+)')

    def _sum_cpu_value(self, data, ncpus, pattern, sink):
        sum_ = 0
        for cpu in range( ncpus ):
            val   = float( data[ pattern.format( cpu ) ] )
            sum_ += val
            if sink:
                sink.accept( val )
        return sum_

    def _generate_summary_from_reader(self, reader, header, start_idx, end_idx):
        load   = StatisticalAccumulator()
        stores = []

        timestamp = ScalarCumulativeValue( 'timestamp', float, 'timestamp' )
        stores.append( timestamp )

        ncpus = int( header[ 'cpu_count' ] )
        freq  = FrequencySink( CPU_LOAD_FREQ_BINS )

        for idx, data in enumerate( reader ):
            if idx==0:
                cputime_classes = self._get_classes( data.keys(), self._pattern_classes_cputime )
                cputime_stores  = []
                for cls in cputime_classes:
                    store = SummedCumulativeValue( cls, float, ValueEnumeration( 'cpu_{}_'+cls, ncpus ) )
                    cputime_stores.append( store )
                    stores.append( store )
                cputime_tuple = SystemCPUTimes.customized_type( cputime_classes )

                net_classes = self._get_classes( data.keys(), self._pattern_classes_net )
                net_stores  = []
                for cls in net_classes:
                    subs = []
                    for quant in [ 'bytes_sent', 'bytes_recv', 'packets_sent', 'packets_recv' ]:
                        name = '{}_{}'.format( cls, quant )
                        subs.append( ScalarCumulativeValue( name, int, 'net_'+name ) )
                    store = CumulativeValueList( cls, subs )
                    net_stores.append( store )
                    stores.append( store )

            if idx==start_idx:
                self._store_values( data, stores )
            elif idx==end_idx:
                self._store_values( data, stores )

            if start_idx <= idx < end_idx:
                # TODO: Use counters!
                sum_ = self._sum_cpu_value( data, ncpus, 'cpu_{}_load', freq )
                load.accept( sum_ )

        for i in range( CPU_LOAD_FREQ_COUNT ):
            freq.counts[ i ] /= (end_idx - start_idx)

        duration = int( timestamp.diff*1000000000 )

        cpu_rates = self._calc_class_rates( cputime_stores )

        cputimes_s = cputime_tuple( **{ v.name: v.start for v in cputime_stores } )
        cputimes_e = cputime_tuple( **{ v.name: v.end for v in cputime_stores } )
        host_load  = SystemCPUTimes.calc_load( timestamp.start, cputimes_s, timestamp.end, cputimes_e )*ncpus

        nics = {}
        for s in net_stores:
            nic_stat  = HostResources.NICStats( *s.value_diffs )
            nics[ s.name ] = TransmittedPacketsResult.for_counters( duration, nic_stat )

        return HostResources( host_load, RateResult( duration, load ), freq, cpu_rates, nics )


# TODO: CumulativeValue etc. should be generalized and be turned into a part of Measr

class ResultError(Exception):
    pass


class ValueGroup(metaclass=ABCMeta):
    @abstractmethod
    def values(self, reader):
        ...


class ValueEnumeration(ValueGroup):
    def __init__(self, field_name_pattern, count):
        self.field_name_pattern = field_name_pattern
        self.count = count

    def values(self, reader):
        for i in range( self.count ):
            yield reader[ self.field_name_pattern.format( i ) ]


class ValueList(ValueGroup):
    def __init__(self, field_names):
        self.field_names = field_names

    def values(self, reader):
        for name in self.field_names:
            yield reader[ name ]


class CumulativeValueList:
    def __init__(self, name, stores):
        self.name   = name
        self.stores = stores

    def store(self, reader):
        for s in self.stores:
            s.store( reader )

    @property
    def end_values(self):
        return [ s.end for s in self.stores ]

    @property
    def value_diffs(self):
        return [ s.diff for s in self.stores ]


class CumulativeValue(metaclass=ABCMeta):
    def __init__(self, name):
        self.name  = name
        self.start = None
        self.end   = None

    def store(self, reader):
        val = self._get_value( reader )
        assert val is not None

        if self.start is None:
            self.start = val
        else:
            assert self.end is None
            self.end = val

    @abstractmethod
    def _get_value(self, reader):
        ...

    @property
    def diff(self):
        return self.end - self.start


class ScalarCumulativeValue(CumulativeValue):
    def __init__(self, name, typ, field_name=None):
        super().__init__( name )
        self.type       = typ
        self.field_name = field_name if field_name is not None else name

    def _get_value(self, reader):
        valstr = reader[ self.field_name ] or 0;
        return self.type( valstr )


class SummedCumulativeValue(CumulativeValue):
    def __init__(self, name, typ, valgrp):
        super().__init__( name )
        self.type   = typ
        self.valgrp = valgrp

    def _get_value(self, reader):
        sum_ = 0
        for val in self.valgrp.values( reader ):
            sum_ += self.type( val or 0 )
        return sum_
