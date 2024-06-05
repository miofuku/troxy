#!/usr/bin/env python3.4

from collections import namedtuple

from measr import AmountResult, AccumulatedValue, MeasuredValue

class NetCounters(namedtuple( 'NetCounters', 'packets_sent bytes_sent packets_recv bytes_recv')):
    __slots__ = ()


# TODO: This module is more general than the monitor in montr.__init__...
class MeasuredBytes(int, MeasuredValue):
    __slots__ = ()

    @property
    def bytes(self):
        return self

    @property
    def kilobytes(self):
        return self.to_kilobytes( self )

    @property
    def megabytes(self):
        return self.to_megabytes( self )

    @classmethod
    def to_kilobytes(cls, value):
        return value/1024*2

    @classmethod
    def to_megabytes(cls, value):
        return value/1024**2


class MeasuredBytesResults(AmountResult):
    __slots__ = ()

    @property
    def bytes(self):
        return self.sum

    @property
    def bytes_per_sec(self):
        return self._amount_per_sec

    @property
    def kilobytes_per_sec(self):
        return MeasuredBytes.to_kilobytes( self._amount_per_sec )

    @property
    def megabytes_per_sec(self):
        return MeasuredBytes.to_megabytes( self._amount_per_sec )


class PacketSize(MeasuredBytes):
    __slots__ = ()


class AccumulatedPacketSize(AccumulatedValue):
    __slots__ = ()

    Empty = None

    @classmethod
    def for_int(cls, packets, bytes_):
        return cls( packets, PacketSize( bytes_ ) )

    @classmethod
    def for_sent_counters(cls, counters):
        return cls.for_int( counters.packets_sent, counters.bytes_sent )

    @classmethod
    def for_recv_counters(cls, counters):
        return cls.for_int( counters.packets_recv, counters.bytes_recv )

    @property
    def packets(self):
        return self.cnt

    @property
    def bytes(self):
        return self.sum

AccumulatedPacketSize.Empty = AccumulatedPacketSize.for_int( 0, 0 )


class PacketSizeResult(MeasuredBytesResults):
    __slots__ = ()

    Empty = None

    @classmethod
    def for_value(cls, duration, packets, bytes_):
        return cls( duration, AccumulatedPacketSize( packets, bytes_ ) )

    @classmethod
    def for_int(cls, duration, packets, bytes_):
        return cls( duration, AccumulatedPacketSize.for_int( packets, bytes_ ) )

    @property
    def packets(self):
        return self.cnt

    @property
    def packets_per_sec(self):
        return self._events_per_sec

PacketSizeResult.Empty = PacketSizeResult( 0, AccumulatedPacketSize.Empty )


class TransmittedPackets(namedtuple( 'TransmittedPackets', 'sent recv' )):
    __slots__ = ()

    Empty = None

    @classmethod
    def for_counters(cls, duration, counters):
        return cls( AccumulatedPacketSize.for_sent_counters( counters ),
                    AccumulatedPacketSize.for_sent_counters( counters ) )

    def __add__(self, value):
        return __class__( self.sent+value[ 0 ], self.recv+value[ 1 ] )

    def __sub__(self, value):
        return __class__( self.sent-value[ 0 ], self.recv-value[ 1 ] )

TransmittedPackets.Empty = TransmittedPackets( AccumulatedPacketSize.Empty, AccumulatedPacketSize.Empty )


class TransmittedPacketsResult(namedtuple( 'TransmittedPacketsResult', 'sent recv' )):
    __slots__ = ()

    Empty = None

    @classmethod
    def for_counters(cls, duration, counters):
        return cls( PacketSizeResult( duration, AccumulatedPacketSize.for_sent_counters( counters ) ),
                    PacketSizeResult( duration, AccumulatedPacketSize.for_recv_counters( counters ) ) )

    @classmethod
    def aggregate(cls, results):
        sent = results[ 0 ].sent.aggregate( [ r.sent for r in results ] )
        recv = results[ 0 ].recv.aggregate( [ r.recv for r in results ] )

        return cls( sent, recv )

TransmittedPacketsResult.Empty = TransmittedPacketsResult( PacketSizeResult.Empty, PacketSizeResult.Empty )


class CPUTimesMixin:
    __slots__ = ()

    @classmethod
    def customized_type(cls, counter_names):
        typ = namedtuple( 'Customized' + cls.__name__, counter_names )
        typ.calc_load = cls.calc_load

        return typ


class ProcessCPUTimes(namedtuple( 'ProcessCPUTimes', 'user system' ), CPUTimesMixin):
    __slots__ = ()

    @classmethod
    def calc_load(cls, s_ts, s_times, e_ts, e_times):
        du = sum( e_times ) - sum( s_times )
        dt = e_ts - s_ts

        return du/dt if dt else 0.0


class ThreadCPUTimes(dict):
    __slots__ = ()

    @classmethod
    def calc_load_per_thread(cls, s_ts, s_thread_times, e_ts, e_thread_times):
        loads = {}

        for tid, e_times in e_thread_times.items():
            s_times = s_thread_times.get( tid, None )
            if s_times is None:
                loads[ tid ] = 0.0
            else:
                loads[ tid ] = s_times.calc_load( s_ts, s_times, e_ts, e_times )

        return loads

    @classmethod
    def calc_load(cls, s_ts, s_thread_times, e_ts, e_thread_times):
        return sum( cls.calc_load_per_thread( s_ts, s_thread_times, e_ts, e_thread_times ).values() )


class SystemCPUTimes(namedtuple( 'SystemCPUTime', 'user system idle' ), CPUTimesMixin):
    __slots__ = ()

    @classmethod
    def calc_load(cls, s_ts, s_times, e_ts, e_times):
        st = sum( s_times )
        et = sum( e_times )
        du = (et - e_times.idle) - (st - s_times.idle)
        dt = et - st

        return du/dt if dt else 0.0


class CoreCPUTimes(list):
    __slots__ = ()

    @classmethod
    def calc_load_per_core(cls, s_ts, s_core_times, e_ts, e_core_times):
        loads = []

        for s_times, e_times in zip( s_core_times, e_core_times ):
            loads.append( s_times.calc_load( s_ts, s_times, e_ts, e_times ) )

        return loads

    @classmethod
    def calc_load(cls, s_ts, s_core_times, e_ts, e_core_times):
        return sum( cls.calc_load_per_core( s_ts, s_core_times, e_ts, e_core_times ) )


class ProcessResources:
    def __init__(self, cpu_load, system_share, tcls_rates, mem_rss, mem_vms, thread_loads=None):
        self.cpu_load     = cpu_load
        self.system_share = system_share
        self.mem_rss      = mem_rss
        self.mem_vms      = mem_vms
        self.tcls_rates   = tcls_rates
        self.thread_loads = thread_loads

    @classmethod
    def aggregate(cls, results):
        sum_mem_rss = sum_mem_vms = 0; sum_sys_share = 0.0
        sum_tcls_rates = {}

        for r in results:
            sum_mem_rss   += r.mem_rss
            sum_mem_vms   += r.mem_vms
            sum_sys_share += r.system_share

            for tcls, val in r.tcls_rates.items():
                sum_tcls_rates[ tcls ] = sum_tcls_rates.get( tcls, 0.0 ) + val

        s = sum( sum_tcls_rates.values() )
        for tcls in r.tcls_rates.keys():
            sum_tcls_rates[ tcls ] /= s

        return cls( results[ 0 ].cpu_load.aggregate( [ r.cpu_load for r in results ] ),
                sum_sys_share/len(results),    sum_tcls_rates, sum_mem_rss, sum_mem_vms )


class HostResources:
    # TODO: use TransmittedPacketsResult or at least NetCounters
    NICStats = namedtuple( 'NICStats', 'bytes_sent bytes_recv packets_sent packets_recv' )

    def __init__(self, cpu_load_mean, cpu_load, cpu_load_freq, cpu_time_rates, nic_stats):
        self.cpu_load_mean  = cpu_load_mean
        self.cpu_load       = cpu_load
        self.cpu_load_freq  = cpu_load_freq
        self.cpu_time_rates = cpu_time_rates
        self.nic_stats      = nic_stats

    @classmethod
    def aggregate(cls, results):
        freq = results[ 0 ].cpu_load_freq.aggregate( [ r.cpu_load_freq for r in results ] )
        load = results[ 0 ].cpu_load.aggregate( [ r.cpu_load for r in results ] )

        means   = [ r.cpu_load_mean for r in results ]
        aggmean = sum( means )/len( means )

        return cls( aggmean, load, freq )
