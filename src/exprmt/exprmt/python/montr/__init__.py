#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from collections import namedtuple
import datetime
import time

from measr import AccumulatedValue, AmountResult, ValueAccumulator
from measr.sysres import ProcessCPUTimes, SystemCPUTimes, PacketSizeResult, TransmittedPacketsResult, PacketSize, \
    AccumulatedPacketSize, NetCounters, MeasuredBytes
import psutil


class ResourceMonitor:
    def __init__(self):
        self.maximum_intervals  = None
        self.interval           = 1.0
        self.processes          = []
        self.monitor_per_thread = False
        self._host              = None
        self.monitor_per_cpu    = False
        self.monitor_per_nic    = False
        self.procrecorders      = []
        self.hostrecorder       = None

    def monitor_process(self, proc, name, threadnames=None):
        self.processes.append( (proc, name, threadnames) )

    def monitor_host(self, name):
        self._host = name

    def init_processes(self):
        return 0

    def run(self):
        assert self.processes or self._host is not None

        self.procrecorders.clear()
        self.hostrecorder = None
        recorders = []

        for proc, name, threadnames in self.processes:
            procrec = ProcessRecorder( proc, name, self.monitor_per_thread, threadnames )
            recorders.append( procrec )
            self.procrecorders.append( procrec )

        if self._host is not None:
            self.hostrecorder = HostRecorder( self._host, percpu=self.monitor_per_cpu, pernic=self.monitor_per_nic )
            recorders.append( self.hostrecorder )

        if recorders:
            self._monitor_resources( recorders )

        return recorders

    def _monitor_resources(self, recorders):
        for rec in recorders:
            rec.start()

        nints = 0

        while self.maximum_intervals is None or nints<self.maximum_intervals:
            succ = True
            for rec in recorders:
                try:
                    rec.measure()
                except psutil.NoSuchProcess:
                    succ = False
                    break
                except psutil.AccessDenied as e:
                    print( 'Error while measuring {}: {}'.format( rec.name, repr( e ) ) )
                    succ = False
                    break

            if not succ:
                break

            self.interval_finished( nints )

            nints += 1

            time.sleep( self.interval )

        for rec in recorders:
            rec.stop()

        return nints

    def interval_finished(self, intno):
        ...


class ResourceRecorder(metaclass=ABCMeta):
    def __init__(self, typ, name):
        self.type       = typ
        self.name       = name
        self.results    = []
        self.start_time = None
        self.start_ts   = None

    def start(self):
        self.start_time = datetime.datetime.now( datetime.timezone.utc ).astimezone()
        self.start_ts   = time.monotonic()

    def stop(self):
        pass

    @abstractmethod
    def measure(self):
        ...

    def monitoring_points(self):
        previous = None

        for res in self.results:
            previous = self.process_monitoring_point( res, previous )

            yield previous

    @abstractmethod
    def process_monitoring_point(self):
        ...


class ProcessRecorder(ResourceRecorder):
    Result = namedtuple( 'Result', 'timestamp cpu_times mem_info cpu_threads' )

    def __init__(self, proc, name, perthread=False, thread_names=None):
        super().__init__( 'proc', name )
        self.proc         = proc
        self.perthread    = perthread
        self.thread_names = thread_names

        self._thread_calcs = {}

    def measure(self):
        res = ProcessRecorder.Result(
                time.monotonic(),
                self.proc.get_cpu_times(),
                self.proc.get_memory_info(),
                self.proc.threads() if self.perthread else None )

        self.results.append( res )

        return res

    def process_monitoring_point(self, current, previous=None):
        curts = current.timestamp

        if previous is None:
            cpu = ProcessCPUMonitoringPoint( curts, current.cpu_times )
        else:
            cpu = previous.cpu.following( curts, current.cpu_times )

        if self.perthread:
            if previous is None:
                threads = ProcessThreadMonitoringPoint( curts, current.cpu_threads )
            else:
                threads = previous.threads.following( curts, current.cpu_threads )
        else:
            threads = None

        mem = ProcessMemoryMonitoringPoint( curts, current.mem_info )

        return ProcessMonitoringPoint( curts, cpu, mem, threads )


class HostRecorder(ResourceRecorder):
    Result = namedtuple( 'Result', 'timestamp cpu_times net_counters' )

    def __init__(self, hostname, percpu=False, pernic=False):
        super().__init__( 'host', hostname )

        self.percpu = percpu
        self.pernic = pernic

    def measure(self):
        res = HostRecorder.Result(
                time.monotonic(),
                psutil.cpu_times( percpu=self.percpu ),
                psutil.net_io_counters( pernic=self.pernic ) )

        self.results.append( res )

        return res

    def process_monitoring_point(self, current, previous=None):
        curts = current.timestamp

        if previous is None:
            cpu_mp_type = HostCoreMonitoringPoint if self.percpu else HostCPUMonitoringPoint
            net_mp_type = HostNICMonitoringPoint if self.pernic else HostNetMonitoringPoint
            cpu = cpu_mp_type( curts, current.cpu_times )
            net = net_mp_type( curts, current.net_counters )
        else:
            cpu = previous.cpu.following( curts, current.cpu_times )
            net = previous.net.following( curts, current.net_counters )

        return HostMonitoringPoint( curts, cpu, net )


class CPUMonitoringPoint:
    __slots__ = 'timestamp', 'load', 'times'

    def __init__(self, timestamp, times, load=0.0):
        self.timestamp = timestamp
        self.times     = times
        self.load      = load

    def following(self, timestamp, times):
        load = self._load_calc( self.timestamp, self.times, timestamp, times,  )
        return self.__class__( timestamp, times, load )

    @property
    def _load_calc(self):
        raise NotImplementedError()


class DetailedCPUMonitoringPoint:
    __slots__ = 'timestamp', 'detailed_loads', 'detailed_times'

    def __init__(self, timestamp, detailed_times, detailed_loads=None):
        self.timestamp  = timestamp
        self.detailed_times = detailed_times
        self.detailed_loads = detailed_loads if detailed_loads is not None else [0.0] * len( self.detailed_times )

    @property
    def load(self):
        return sum( self.detailed_loads )

    @property
    def times(self):
        sum_times = [0] * len( self.detailed_times[ 0 ] )
        for cpu_times in self.detailed_times:
            for i, t in enumerate( cpu_times ):
                sum_times[ i ] += t
        return self.detailed_times[ 0 ].__class__._make( sum_times )

    def following(self, timestamp, detailed_times):
        detailed_loads = []
        for ct, last_ct in zip( detailed_times, self.detailed_times ):
            detailed_loads.append( self._load_calc( self.timestamp, last_ct, timestamp, ct ) )
        return self.__class__( timestamp, detailed_times, detailed_loads )

    @property
    def _load_calc(self):
        raise NotImplementedError()


class ProcessCPUMonitoringPoint(CPUMonitoringPoint):
    __slots__ = ()

    @property
    def _load_calc(self):
        return ProcessCPUTimes.calc_load


class ProcessThreadMonitoringPoint(DetailedCPUMonitoringPoint):
    __slots__ = ()

    @property
    def _load_calc(self):
        return ProcessCPUTimes.calc_load


class ProcessMemoryMonitoringPoint:
    __slots__ = 'timestamp', 'values'

    def __init__(self, timestamp, values):
        self.timestamp = timestamp
        self.values    = values


class ProcessMonitoringPoint:
    __slots__ = 'timestamp', 'cpu', 'mem', 'threads'

    def __init__(self, timestamp, cpu, mem, threads):
        self.timestamp = timestamp
        self.cpu       = cpu
        self.mem       = mem
        self.threads   = threads


class HostCPUMonitoringPoint(CPUMonitoringPoint):
    __slots__ = ()

    @property
    def _load_calc(self):
        return SystemCPUTimes.calc_load


class HostCoreMonitoringPoint(DetailedCPUMonitoringPoint):
    __slots__ = ()

    @property
    def _load_calc(self):
        return SystemCPUTimes.calc_load


# TODO: packets_recv=19231830, errin=0, errout=0, bytes_recv=12277858468, dropin=0, dropout=0, packets_sent=16461219, bytes_sent=4160163355
class NetMonitoringPoint:
    __slots__ = 'timestamp', 'counters', 'result'

    def __init__(self, timestamp, counters, result=None):
        self.timestamp = timestamp
        self.counters  = counters
        self.result    = result

    def following(self, timestamp, counters):
        result = self._following_result( self.timestamp, self.counters, timestamp, counters )

        return self.__class__( timestamp, counters, result )

    def _following_result(self, s_ts, s_counters, e_ts, e_counters):
        dt  = int( ( e_ts-s_ts ) * 10**9 )

        dsp = e_counters.packets_sent - s_counters.packets_sent
        dsb = e_counters.bytes_sent - s_counters.bytes_sent
        ds  = PacketSizeResult.for_value( dt, dsp, dsb )

        drp = e_counters.packets_recv - s_counters.packets_recv
        drb = e_counters.bytes_recv - s_counters.bytes_recv
        dr  = PacketSizeResult.for_value( dt, drp, drb )

        return TransmittedPacketsResult( ds, dr )


class HostNetMonitoringPoint(NetMonitoringPoint):
    __slots__ = ()


class HostNICMonitoringPoint(NetMonitoringPoint):
    __slots__ = 'nic_counters', 'nic_results'

    def __init__(self, timestamp, nic_counters, nic_results=None):
        self.nic_counters = nic_counters

        if nic_results:
            self.nic_results = nic_results
        else:
            self.nic_results = {nic: TransmittedPacketsResult.Empty for nic in self.nic_counters.keys()}

        sent = ValueAccumulator()
        recv = ValueAccumulator()

        for counters in nic_counters.values():
            sent.add( counters.packets_sent, counters.bytes_sent )
            recv.add( counters.packets_recv, counters.bytes_recv )

        counters = NetCounters( sent.cnt, sent.sum, recv.cnt, recv.sum )
        result   = TransmittedPacketsResult.aggregate( [res for res in self.nic_results.values()] )

        super().__init__( timestamp, counters, result )

    def following(self, timestamp, nic_counters):
        nic_results = {}
        for nic, counters in nic_counters.items():
            nic_results[ nic ] = self._following_result( self.timestamp, self.nic_counters[ nic ], timestamp, counters )

        return self.__class__( timestamp, nic_counters, nic_results )


class HostMonitoringPoint:
    __slots__ = 'timestamp', 'cpu', 'net'

    def __init__(self, timestamp, cpu, net):
        self.timestamp = timestamp
        self.cpu       = cpu
        self.net       = net
