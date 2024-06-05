#!/usr/bin/env python3.4

from collections import namedtuple
import os, re, glob, sys
from pathlib import Path

from consl import Consl
from consl.cmds import CommandGrammarFamily
from intrpt.valprocs import Converter
from measr import StatisticalAccumulator
from measr.translt import StatisticalAccumulatorField, AmountResultField, ResultPrefixProcessor, \
    DurationResultField, RateResultField, TransmittedPacketsResultField, ProcessResourcesField, HostResourcesField
from montr.translt_in import CPULoadFrequencyField, ProcessResourcesFileMapping, HostResourcesFileMapping
from plib.shell.java import JavaThreadClass
from plib.utils import Value
from reptor.system import Reptor
from translt import DirectMapping, ListToSingleObject
from translt.data import CSV, KeyValueFormat, JSON, YAML
from translt.directories import DirectoryMapping, FileToDirectory, FileToDirectoryPattern, \
    UnboundFileEnumeration, FileEnumeration, DirectoryPatternFormat, DirectoryPatternToDirectory
from translt.fieldnams import Fieldnames
from translt.fields import CompositeField, DirectContainerField, FieldMetaType, FloatField, StringField, \
    IntegerField, DictionaryField, NamedSequenceField, AttributeMappingList
from translt.fieldsets import RecordFieldSet
from translt.files import PathProcessor, StreamToFile
from translt.streams import ObjectStreamMapping, PlainStreamMapping
from translt.translators import ObjectNotFound


class SingleResult:
    def __init__(self, result):
        self._result = result

    @classmethod
    def aggregate(cls, results):
        return cls( results[ 0 ]._result.aggregate( [ r._result for r in results ] ) )

    @classmethod
    def field(cls, result, field_name=Fieldnames.Integrated):
        return CompositeField( cls, RecordFieldSet ).init_field( result, cls.prop_name ).attribute().construct().name( field_name ).end()


class ClientRequests(SingleResult):
    prop_name = 'request_latency'

    @property
    def request_latency(self):
        return self._result


class ConsensusInstances(SingleResult):
    prop_name = 'consensus_time'

    @property
    def consensus_time(self):
        return self._result


class ProcessedRequests(SingleResult):
    prop_name = 'process_time'

    @property
    def process_time(self):
        return self._result


class ExecutedRequests(SingleResult):
    prop_name = 'batch_size'

    @property
    def batch_size(self):
        return self._result


class AppliedCheckpoints(SingleResult):
    prop_name = 'consensus_skipped'

    @property
    def consensus_skipped(self):
        return self._result


class IssuedRequests:
    def __init__(self, clientid, request_latency):
        self.clientid        = clientid
        self.request_latency = request_latency

    @classmethod
    def aggregate(cls, results):
        return cls( None, results[ 0 ].request_latency.aggregate( [ r.request_latency for r in results ] ) )

    @classmethod
    def field(cls, request_latency, fn_cliid='clientid', fn_reqlat=None):
        return CompositeField( cls, RecordFieldSet ) \
                    .start_integer( 'clientid' ).attribute().construct().name( fn_cliid ).end() \
                    .init_field( request_latency, 'request_latency' ).attribute().construct().name( fn_reqlat ).end()



class ResultsSummary:
    def __init__(self):
        self.date            = None
        self.platform        = None
        self.benchmark       = None
        self.protocol        = None
        self.crypto          = None
        self.nclients        = None
        self.reqs_per_client = None
        self.batchsize_min   = None
        self.batchsize_max   = None
        self.request_size    = None
        self.reply_size      = None
        self.config          = None
        self.cores           = None
        self.desc            = None

        self.client_requests = None
        self.client_issued   = None
        self.clients_trans   = None
        self.clients_procres = None
        self.clients_hostres = None

        self.replicas_execd    = None
        self.replicas_appld    = None
        self.replicas_cons     = None
        self.replicas_trans    = None
        self.replicas_procres  = None
        self.replicas_hostres  = None
        self.replicas_procreqs = None



class Report(DirectContainerField):
    metatype = FieldMetaType.dictionary

    _stafld = StatisticalAccumulatorField( RecordFieldSet ) \
                    .init_mean().end() \
                    .init_min().end() \
                    .init_max().end() \
                    .init_approx_stddev().end()

    _stanominfld = StatisticalAccumulatorField( RecordFieldSet ) \
                    .init_mean().end() \
                    .init_max().end() \
                    .init_approx_stddev().end()

    def __init__(self, basefld):
        super().__init__( RecordFieldSet )

        self.basefld = basefld

    def _value(self, obj, path=None):
        try:
            return self.basefld.get_translator().get_value( obj, path )
        except ObjectNotFound:
            return None



class ClientThroughputReport(Report):
    def output_subfields(self, cntxt, ressum):
        yield None, 'reqs_per_sec', FloatField, \
                self._value( ressum, ('client_requests', 'request_latency', 'events_per_sec') )
        if ressum.client_issued and ressum.client_issued.min:
            clispread = ressum.client_issued.max / ressum.client_issued.min
        else:
            clispread = None
        yield None, 'spread', FloatField, clispread


class ReplicaThroughputReport(Report):
    def __init__(self, basefld, repno):
        super().__init__( basefld )
        self.repno = repno

    def output_subfields(self, cntxt, ressum):
        yield None, 'procd_per_sec', FloatField, \
                self._value( ressum, ('replicas_procreqs', self.repno, 'process_time', 'events_per_sec' ) )
        yield None, 'execd_per_sec', FloatField, \
                self._value( ressum, ('replicas_execd', self.repno, 'batch_size', 'amount_per_sec') )


class RequestThroughputReport(Report):
    def output_subfields(self, cntxt, ressum):
        yield None, 'clients', ClientThroughputReport( self.basefld ), ressum
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), ReplicaThroughputReport( self.basefld, repno ), ressum


class ReplicaConsensusThroughputReport(Report):
    def __init__(self, basefld, repno):
        super().__init__( basefld )
        self.repno = repno

    def output_subfields(self, cntxt, ressum):
        agreed = self._value( ressum, ('replicas_cons', self.repno, 'consensus_time', 'events_per_sec' ) ) or 0
        yield None, 'agreed', FloatField, agreed

        skipped = self._value( ressum, ('replicas_appld', self.repno, 'consensus_skipped', 'amount_per_sec' ) ) or 0
        yield None, 'skipped', FloatField, skipped

        yield None, 'total', FloatField, agreed+skipped



class ConsensusThroughputReport(Report):
    def output_subfields(self, cntxt, ressum):
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), ReplicaConsensusThroughputReport( self.basefld, repno ), ressum


class ReplicaCheckpointsReport(Report):
    _ckpfld = AmountResultField( RecordFieldSet ) \
                    .init_events_per_sec().name( 'per_sec' ).end() \
                    .init_amount_per_sec().name( 'per_sec' ).end() \
                    .init_summary( Report._stafld ).integrate().end()
    _ckpfld.name_processor( ResultPrefixProcessor( 'skippedcons_', '' ) )

    def __init__(self, basefld, repno):
        super().__init__( basefld )
        self.repno = repno

    def output_subfields(self, cntxt, ressum):
        yield None, 'replica_{}'.format( self.repno ), self._ckpfld, \
            self._value( ressum, ('replicas_appld', self.repno, 'consensus_skipped') )

class CheckpointsReport(Report):
    def output_subfields(self, cntxt, ressum):
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), \
                    ReplicaCheckpointsReport( self.basefld, repno ), ressum


class ReplicaBatchesReport(Report):
    _batfld = AmountResultField( RecordFieldSet ) \
                    .init_events_per_sec().name( 'per_sec' ).end() \
                    .init_summary( Report._stafld ).integrate().end()
    _batfld.name_processor( ResultPrefixProcessor( 'reqs_', '' ) )

    def __init__(self, basefld, repno):
        super().__init__( basefld )
        self.repno = repno

    def output_subfields(self, cntxt, ressum):
        yield None, None, self._batfld, \
                self._value( ressum, ('replicas_execd', self.repno, 'batch_size') )


class BatchesReport(Report):
    def output_subfields(self, cntxt, ressum):
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), \
                    ReplicaBatchesReport( self.basefld, repno ), ressum


class ReplicaLatencyReport(Report):
    _durfld = DurationResultField( RecordFieldSet ).init_summary( Report._stafld ).integrate().end()

    def __init__(self, sumfld, repno):
        super().__init__( sumfld )
        self.repno = repno

    def output_subfields(self, cntxt, ressum):
        yield None, 'procd', self._durfld, \
                self._value( ressum, ('replicas_procreqs', self.repno, 'process_time') )
        yield None, 'cons', self._durfld, \
                self._value( ressum, ('replicas_cons', self.repno, 'consensus_time') )


class SummaryLatencyReport(Report):
    def output_subfields(self, cntxt, ressum):
        yield None, 'clients', self._stafld, \
                self._value( ressum, ('client_requests', 'request_latency', 'summary') )

        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), ReplicaLatencyReport( self.basefld, repno ), ressum


class SummaryReport(Report):
    def _header(self, ressum):
        yield None, 'date', StringField, self._value( ressum, ('date',) )
        yield None, 'desc', StringField, self._value( ressum, ('desc',) )
        yield None, 'platform', StringField, self._value( ressum, ('platform',) )
        yield None, 'benchmark', StringField, self._value( ressum, ('benchmark',) )
        yield None, 'protocol', StringField, self._value( ressum, ('protocol',) )
        yield None, 'crypto', StringField, self._value( ressum, ('crypto',) )
        yield None, 'nclients', IntegerField, self._value( ressum, ('nclients',) )
        yield None, 'reqs_per_client', IntegerField, self._value( ressum, ('reqs_per_client',) )
        yield None, 'batchsize_min', IntegerField, self._value( ressum, ('batchsize_min',) )
        yield None, 'batchsize_max', IntegerField, self._value( ressum, ('batchsize_max',) )
        yield None, 'request_size', IntegerField, self._value( ressum, ('request_size',) )
        yield None, 'reply_size', IntegerField, self._value( ressum, ('reply_size',) )
        yield None, 'config', StringField, self._value( ressum, ('config',) )
        yield None, 'cores', IntegerField, self._value( ressum, ('cores',) )

class PerformanceReport(SummaryReport):
    def output_subfields(self, cntxt, ressum):
        yield from self._header( ressum )
        yield None, 'requests', RequestThroughputReport( self.basefld ), ressum
        yield None, 'instances_per_sec', ConsensusThroughputReport( self.basefld ), ressum
        yield None, 'batches', BatchesReport( self.basefld ), ressum
        yield None, 'checkpoints', CheckpointsReport( self.basefld ), ressum
        yield None, 'latency', SummaryLatencyReport( self.basefld ), ressum



class TransmittedDirReportBase(Report):
    def __init__(self, basefld, mode, procno, dir_):
        super().__init__( basefld )
        self.mode   = mode
        self.procno = procno
        self.dir    = dir_

    def output_subfields(self, cntxt, ressum):
        if ressum is None:
            javatrans = None
        elif self.mode==0:
            javatrans = ressum.clients_trans[ self.procno ] if ressum.clients_trans else None
        else:
            javatrans = ressum.replicas_trans[ self.procno ] if ressum.replicas_trans else None

        yield self._java_item( self._dir( javatrans ) )

        for nic in [ 'eth0', 'eth1', 'eth2', 'eth3', 'eth4' ]:
            elem    = 'clients_hostres' if self.mode==0 else 'replicas_hostres'
            hostdir = self._dir( self._value( ressum, (elem, self.procno, 'nic_stats', nic) ) )
            yield self._nic_item( nic, hostdir )

    def _dir(self, trans):
        if trans is None:
            return None
        else:
            return trans.sent if self.dir==0 else trans.recv


class DataRateDirReport(TransmittedDirReportBase):
    def _java_item(self, javadir):
        return None, 'java', FloatField, javadir.amount_per_sec if javadir else None

    def _nic_item(self, nic, hostdir):
        return None, nic, FloatField, hostdir.amount_per_sec if hostdir else None


class PacketsDirReport(TransmittedDirReportBase):
    _javafld = AmountResultField( RecordFieldSet ) \
                    .init_events_per_sec().name( 'per_sec' ).end() \
                    .init_summary( Report._stafld ).integrate().end()
    _javafld.name_processor( ResultPrefixProcessor( 'chunksize_', 'chunks_' ) )

    _hoststa = StatisticalAccumulatorField( RecordFieldSet )
    _hoststa.init_mean()
    _hostfld = AmountResultField( RecordFieldSet ) \
                    .init_events_per_sec().name( 'per_sec' ).end() \
                    .init_summary( _hoststa ).integrate().end()
    _hostfld.name_processor( ResultPrefixProcessor( 'packetsize_', 'packets_' ) )

    def _java_item(self, javadir):
        return None, 'java', self._javafld, javadir if javadir else None

    def _nic_item(self, nic, hostdir):
        return None, nic, self._hostfld, hostdir if hostdir else None



class TransmittedPacketsResultProcessReport(Report):
    def __init__(self, basefld, mode, procno, dirreptyp):
        super().__init__( basefld )
        self.sent = dirreptyp( basefld, mode, procno, 0 )
        self.recv = dirreptyp( basefld, mode, procno, 1 )

    def output_subfields(self, cntxt, ressum):
        yield None, 'sent', self.sent, ressum
        yield None, 'recv', self.recv, ressum


class TransmittedPacketsResultSummaryReport(Report):
    def __init__(self, basefld, dirreptyp):
        super().__init__( basefld )
        self.dirreptyp = dirreptyp

    def output_subfields(self, cntxt, ressum):
        if ressum.clients_hostres:
            clicnt = len( ressum.clients_hostres )
        elif ressum.clients_trans:
            clicnt = len( ressum.clients_trans )
        else:
            clicnt = 1

        for clino in range( clicnt ):
            yield None, 'clients_{}'.format( clino ), \
                TransmittedPacketsResultProcessReport( self.basefld, 0, clino, self.dirreptyp ), ressum
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), \
                    TransmittedPacketsResultProcessReport( self.basefld, 1, repno, self.dirreptyp ), ressum


class TransmittedPacketsResultReport(Report):
    def output_subfields(self, cntxt, ressum):
        yield None, 'datarate', TransmittedPacketsResultSummaryReport( self.basefld, DataRateDirReport ), ressum
        yield None, 'packets', TransmittedPacketsResultSummaryReport( self.basefld, PacketsDirReport ), ressum



class CPUProcessReport(Report):
    def __init__(self, basefld, mode, procno):
        super().__init__( basefld )
        self.mode   = mode
        self.procno = procno

    _tcls_rates = DictionaryField( RecordFieldSet ).add_fieldgroup( FloatField, [tcls.name for tcls in JavaThreadClass] )


    def output_subfields(self, cntxt, ressum):
        pref = 'clients' if self.mode==0 else 'replicas'

        yield None, 'proc_load_mean', FloatField, \
                self._value( ressum, (pref+'_procres', self.procno, 'cpu_load', 'summary', 'mean' ) )
        yield None, 'host_load_mean', FloatField, \
                self._value( ressum, (pref+'_hostres', self.procno, 'cpu_load_mean' ) )
        yield None, 'sys_share', FloatField, \
                self._value( ressum, (pref+'_procres', self.procno, 'system_share') )
        yield None, 'core_load', CPULoadFrequencyField( self._config, 'cnt_{}' ), \
                self._value( ressum, (pref+'_hostres', self.procno, 'cpu_load_freq') )
        yield None, 'tcls_rates', self._tcls_rates, \
                self._value( ressum, (pref+'_procres', self.procno, 'tcls_rates') )


class CPUThreadReport(Report):
    metatype = FieldMetaType.sequence

    _entryfld = NamedSequenceField( RecordFieldSet ) \
                    .start_string( 0 ).name( 'thread' ).end() \
                    .start_float( 1 ).name( 'load' ).end()

    def __init__(self, basefld, mode, procno):
        super().__init__( basefld )
        self.mode   = mode
        self.procno = procno

    def output_subfields(self, cntxt, ressum):
        pref = 'clients' if self.mode==0 else 'replicas'

        procres = self._value( ressum, (pref+'_procres', self.procno ) )

        if procres and procres.thread_loads:
            apps    = filter( lambda x: x[ 0 ].thread_class==JavaThreadClass.app, procres.thread_loads.items() )
            sortnam = sorted( apps, key=lambda x: x[ 0 ].name )

            for i, (tinf, load) in enumerate( sortnam ):
                yield i, None, self._entryfld, (tinf.name, load)


class CPUReport(Report):
    def __init__(self, basefld, typ):
        super().__init__( basefld )
        self.type = typ

    def output_subfields(self, cntxt, ressum):
        if ressum.clients_hostres:
            clicnt = len( ressum.clients_hostres )
        elif ressum.clients_procres:
            clicnt = len( ressum.clients_procres )
        else:
            clicnt = 1

        for clino in range( clicnt ):
            yield None, 'client_{}'.format( clino ), self.type( self.basefld, 0, clino ), ressum
        for repno in range( 4 ):
            yield None, 'replica_{}'.format( repno ), self.type( self.basefld, 1, repno ), ressum


class ResourcesReport(SummaryReport):
    def output_subfields(self, cntxt, ressum):
        yield from self._header( ressum )
        yield None, 'transd', TransmittedPacketsResultReport( self.basefld ), ressum
        yield None, 'cpu', CPUReport( self.basefld, CPUProcessReport ), ressum
        yield None, 'threads', CPUReport( self.basefld, CPUThreadReport ), ressum



class NICUtilizationDirReport(Report):
    _Entry = namedtuple( '_Entry', 'proc thread rate')

    _entryfld = NamedSequenceField( RecordFieldSet ) \
                    .start_string( 0 ).name( 'proc' ).end() \
                    .start_string( 1 ).name( 'nic' ).end() \
                    .start_float( 2 ).name( 'rate' ).end()

    def __init__(self, basefld, attr, dir_):
        super().__init__( basefld )
        self.attr = attr
        self.dir  = dir_

    def _get_utils(self, cnt, procnam, attr, ressum):
        utils = []
        for procno in range( cnt ):
            for nic in 'eth0', 'eth1', 'eth2', 'eth3', 'eth4':
                val = self._value( ressum, (attr, procno, 'nic_stats', nic, self.dir, self.attr ) )
                if val is not None:
                    utils.append( self._Entry( '{}_{}'.format( procnam, procno ), nic, val ) )
        utils.sort( key=lambda x: x.rate, reverse=True )

        return utils

    def output_subfields(self, cntxt, ressum):
        if ressum.replicas_hostres:
            reputils = self._get_utils( 4, 'replica', 'replicas_hostres', ressum )
        else:
            reputils = [None]*2

        for i, u in enumerate( reputils[ :2 ] ):
            yield None, i, self._entryfld, u

        yield None, 'min', self._entryfld, reputils[ -1 ]

        if ressum.clients_hostres:
            cliutils = self._get_utils( len( ressum.clients_hostres ), 'client', 'clients_hostres', ressum )
        else:
            cliutils = [None]

        yield None, 'clients', self._entryfld, cliutils[ 0 ]


class NICUtilizationReport(SummaryReport):
    def __init__(self, basefld, attr):
        super().__init__( basefld )
        self.sent = NICUtilizationDirReport( basefld, attr, 'sent' )
        self.recv = NICUtilizationDirReport( basefld, attr, 'recv' )

    def output_subfields(self, cntxt, ressum):
        yield None, 'sent', self.sent, ressum
        yield None, 'recv', self.recv, ressum


class HostLoadReport(Report):
    def output_subfields(self, cntxt, ressum):
        for i in range( 4 ):
            yield None, 'load_replica' + str( i ), FloatField, \
                    self._value( ressum, ('replicas_hostres', i, 'cpu_load_mean' ) )

        maxclient = max( hr.cpu_load_mean for hr in ressum.clients_hostres ) if ressum.clients_hostres else None

        yield None, 'load_client_max', FloatField, maxclient


class ThreadLoadReport(SummaryReport):
    _Entry = namedtuple( '_Entry', 'proc thread load')

    _entryfld = NamedSequenceField( RecordFieldSet ) \
                    .start_string( 0 ).name( 'proc' ).end() \
                    .start_string( 1 ).name( 'thread' ).end() \
                    .start_float( 2 ).name( 'load' ).end()

    def _get_loads(self, procnam, procreslst):
        loads = []

        for i, procres in enumerate( procreslst ):
            if procres and procres.thread_loads:
                for tinf, load in procres.thread_loads.items():
                    if tinf.thread_class==JavaThreadClass.app:
                        loads.append( self._Entry( '{}_{}'.format( procnam, i ), tinf.name, load ) )

        loads.sort( key=lambda x: x.load, reverse=True )

        return loads

    def output_subfields(self, cntxt, ressum):
        if ressum.replicas_procres:
            reploads = self._get_loads( 'replica', ressum.replicas_procres ) or [None]*4
        else:
            reploads = [None]*4

        for i, e in enumerate( reploads[ :4 ] ):
            yield None, i, self._entryfld, e

        if ressum.clients_procres:
            cliloads = self._get_loads( 'client', ressum.clients_procres )
        else:
            cliloads = None

        yield None, 'clients', self._entryfld, cliloads[ 0 ] if cliloads else None


class ExcerptReport(SummaryReport):
    _latfld = DurationResultField( RecordFieldSet ) \
                .init_events_per_sec().name( 'per_sec' ).end() \
                .init_summary( Report._stanominfld ).integrate().end()
    _latfld.name_processor( ResultPrefixProcessor( 'lat_', 'reqs_' ) )

    _batfld = AmountResultField( RecordFieldSet ) \
                    .init_events_per_sec().name( 'reqs_per_sec' ).end() \
                    .start_float( 'mean' ).attribute().name( 'reqs_mean' ).end()

    def output_subfields(self, cntxt, ressum):
        yield from self._header( ressum )
        yield None, 'clients', self._latfld, \
                self._value( ressum, ('client_requests', 'request_latency') )
        yield None, 'batches', self._batfld, \
                self._value( ressum, ('replicas_execd', 0, 'batch_size') )
        yield None, 'datarate', NICUtilizationReport( self.basefld, 'amount_per_sec' ), ressum
        yield None, 'packets', NICUtilizationReport( self.basefld, 'events_per_sec' ), ressum
        yield None, 'hosts', HostLoadReport( self.basefld ), ressum
        yield None, 'threads', ThreadLoadReport( self.basefld ), ressum

# TODO: There was somewhere a boolean converter...
def is_true(value):
    if isinstance( value, bool):
        return value
    elif isinstance( value, str ):
        return value.lower() in ('true', 't')
    else:
        return value

def value(value, default):
    return value if value!=Value.Undefined else default


class HeaderInputMapping(DirectMapping):
    _core_det_pattern   = re.compile( r'-0(?:-\d{1,2})?_(?:(12)|12-(\d{2}))C$' )
    _core_sim_pattern   = re.compile( r'-(?:(\d)N)?(\d{1,2})C$' )

    def read_to(self, ressum, resdir):
        respath    = Path( resdir )
        system     = Reptor()
        system.load_config( respath / 'config' / 'system.cfg' )
        system.load_run( respath / 'control.txt' )

        ressum.date            = system.current_run.starttime.replace( microsecond=0, tzinfo=None )
        ressum.nclients        = value( system.benchmark_settings.client_count, 1 )
        ressum.reqs_per_client = value( system.benchmark_settings.requests_per_client, 1 )
        ressum.batchsize_min   = value( system.global_settings.batchsize_min, 1 )
        ressum.batchsize_max   = value( system.global_settings.batchsize_max, 1 )
        ressum.request_size    = value( system.benchmark_settings.request_size, 0 )
        ressum.reply_size      = value( system.benchmark_settings.reply_size, 0 )
        ressum.protocol        = value( system.benchmark_settings.protocol_variant, system.global_settings.protocol )

        if 'sgxs' in resdir:
            ressum.platform = 'sgxs'
        elif 'beagles' in resdir:
            ressum.platform = 'beagles'

        ressum.benchmark = value( system.benchmark_settings.benchmark_name, Value.Undefined )

        def cert_provider(certalgo):
            if certalgo=='TMAC_HMAC_SHA256':
                return certalgo + '({})'.format( system.global_settings.replicas_trusted )
            else:
                return certalgo + '(java)'

        clicrypto = cert_provider( system.global_settings.clients_cert_algo )
        repcrypto = cert_provider( system.global_settings.replicas_strong_cert_algo )

        if system.global_settings.replies_cert_algo in (Value.Undefined, 'default'):
            repliescrypto = ''
        else:
            repliescrypto = '<>' + cert_provider( system.global_settings.replies_cert_algo )

        ressum.crypto = '{}{}/{}<{}'.format( clicrypto, repliescrypto, repcrypto, system.global_settings.message_digest )

        resdirname = respath.name

        ressum.config = resdirname[ 20: ]
        if ressum.config.endswith( '-' + str( ressum.nclients ) ):
            ressum.config = ressum.config[ :ressum.config.rfind( '-' ) ]

        m = self._core_det_pattern.search( ressum.config )
        if m:
            ressum.cores = int( m.group(1) if m.group(1) else m.group(2) ) - 11

        m = self._core_sim_pattern.search( ressum.config )
        if m:
            if m.group( 1 ) is None:
                ressum.cores = m.group( 2 )
            else:
                ressum.cores = int( m.group(1) ) * int( m.group(2) )



class HeadedResultFileSummary(ObjectStreamMapping):
    def read_from(self, stream):
        return [ self.data_format.object_reader( stream, self.record_mapping ).read_object() ]


class ListedResultFileSummary(ObjectStreamMapping):
    def read_from(self, stream):
        reslist = self.data_format.read( stream, self.record_mapping )

        return [ reslist[ 0 ].aggregate( reslist ) ]


class IssuedRequestsFileSummary(ObjectStreamMapping):
    def read_from(self, stream):
        reslist = self.data_format.read( stream, self.record_mapping )

        agg = StatisticalAccumulator()
        for r in reslist:
            agg.accept( r.request_latency.summary.cnt )

        return [ agg ]

# TODO: Introduce processors for directories and realise aggregation as a processors (same might hold for ListToSingleObject)
class ResultDirectorySummary(DirectoryPatternFormat):
    def __init__(self, submap):
        self.submap = submap

    def read(self, directory, file_pattern, pattern_kwargs, file_reader):
        res_list = self.submap.read( directory, file_pattern, pattern_kwargs, file_reader )

        return [ res_list[ 0 ].aggregate( res_list ) ] if res_list else []



class ResultsSummaryMapping(DirectoryMapping):
    _resdir_pattern = re.compile( r'\d{4}_\d{2}_\d{2}\-\d{2}_\d{2}_\d{2}' )

    @classmethod
    def _single_file_mapping(cls, typfld, datfmt, filnam):
        filmap = StreamToFile( ObjectStreamMapping( datfmt, datfmt.record_mapping( typfld ) ) )
        dirmap = FileToDirectory( filnam, filmap )

        return dirmap


    def __init__(self, data_format, sumfile, cont_on_error=False):
        self._datfmt       = data_format
        self._sumfilname   = sumfile
        self.cont_on_error = cont_on_error

        self._genmap = AttributeMappingList()

        self._sumfld = CompositeField( ResultsSummary, RecordFieldSet )
        self._sumdir = self._single_file_mapping( self._sumfld, data_format, sumfile )

        defext = data_format.default_file_extension

        self._repprfdir = self._single_file_mapping( PerformanceReport( self._sumfld ),
                                    data_format, 'report_performance.{}'.format( defext ) )
        self._represdir = self._single_file_mapping( ResourcesReport( self._sumfld ),
                                    data_format, 'report_resources.{}'.format( defext) )
        self._repexcdir = self._single_file_mapping( ExcerptReport( self._sumfld ),
                                    data_format, 'report_excerpt.{}'.format( defext ) )

        self.init_header()
        self.init_client_requests()
        self.init_client_issued()
        self.init_trans_data()
        self.init_executed_requests()
        self.init_processed_requests()
        self.init_consensus_instances()
        self.init_applied_checkpoints()
        self.init_process_resources()
        self.init_host_resources()


    _stafld = StatisticalAccumulatorField( RecordFieldSet ) \
                        .init_cnt().end() \
                        .init_sum().end() \
                        .init_mean().end() \
                        .init_min().end() \
                        .init_max().end() \
                        .init_approx_stddev().end()

    _gen_durfld = DurationResultField( RecordFieldSet ) \
                        .init_duration( IntegerField ).name( 'time' ).end() \
                        .init_summary( _stafld ).integrate().end()

    _gen_amtfld = AmountResultField( RecordFieldSet ) \
                        .init_duration( IntegerField ).name( 'time' ).end() \
                        .init_summary( _stafld ).integrate().end()

    _sum_durfld = DurationResultField( RecordFieldSet ) \
                        .init_duration( IntegerField ).end() \
                        .init_events_per_sec().name( 'per_sec' ).end() \
                        .init_summary( _stafld ).integrate().end()

    _sum_amtfld = AmountResultField( RecordFieldSet ) \
                        .init_duration( IntegerField ).end() \
                        .init_events_per_sec().name( 'per_sec' ).end() \
                        .init_amount_per_sec().name( 'per_sec' ).end() \
                        .init_summary( _stafld ).integrate().end()

    _sum_ratfld = RateResultField( RecordFieldSet ) \
                        .init_duration( IntegerField ).end() \
                        .init_summary( _stafld ).integrate().end()


    _gen_datfmt = CSV()

    _gen_filtodir    = FileToDirectoryPattern()
    _gen_clifiles    = UnboundFileEnumeration( _gen_filtodir, index_name='cid' )
    _gen_clisumfiles = ResultDirectorySummary( _gen_clifiles )
    _gen_repfiles    = FileEnumeration( _gen_filtodir, 4, index_name='rid' )
    _gen_tskfiles    = ResultDirectorySummary( UnboundFileEnumeration( _gen_filtodir, index_name='tno' ) )
    _gen_reptskfiles = FileEnumeration( _gen_tskfiles, 4, index_name='rid' )


    def _file_mapping(self, typ, field):
        return StreamToFile( typ( self._gen_datfmt, self._gen_datfmt.record_mapping( field ) ),
                read_proc=PathProcessor.IsFile, on_error=self._on_file_error )

    def _on_file_error(self, error, file):
        print( 'Error while parsing {}: {!r}'.format( file, error ), file=sys.stderr )
        if not self.cont_on_error:
            raise error

    def _add_file_list_to_gen(self, attr_name, file_list, file_pattern, file_mapping, single_object=False):
        dirmap = DirectoryPatternToDirectory( file_list, 'results/' + file_pattern, file_mapping )

        if single_object:
            dirmap = ListToSingleObject( dirmap )

        self._genmap.add_nested( attr_name, dirmap )


    def _add_single_result_to_gen(self, attr_name, file_list, file_pattern, field, single_object=False):
        filmap = self._file_mapping( HeadedResultFileSummary, field )
        self._add_file_list_to_gen( attr_name, file_list, file_pattern, filmap, single_object )


    def _replicas_field(self, field):
        return NamedSequenceField( RecordFieldSet ).add_field( field ).elements_namepattern( 'replica_{}' ).max_count( 4 )

    def _clients_field(self, field):
        return NamedSequenceField( RecordFieldSet ).add_field( field ).elements_namepattern( 'client_{}' )


    def init_header(self):
        self._genmap.append( HeaderInputMapping() )

        desfil = ListToSingleObject( StreamToFile( PlainStreamMapping(), read_proc=PathProcessor.IsFile,  ) )
        self._genmap.add_nested( 'desc', FileToDirectory( 'results/description.txt', desfil ) )

        self._sumfld \
                .start_string( 'date' ).attribute().end() \
                .start_string( 'desc' ).attribute().end() \
                .start_string( 'platform' ).attribute().end() \
                .start_string( 'benchmark' ).attribute().end() \
                .start_string( 'protocol' ).attribute().end() \
                .start_string( 'crypto' ).attribute().end() \
                .start_integer( 'nclients' ).attribute().end() \
                .start_integer( 'reqs_per_client' ).attribute().end() \
                .start_integer( 'batchsize_min' ).attribute().end() \
                .start_integer( 'batchsize_max' ).attribute().end() \
                .start_integer( 'request_size' ).attribute().end() \
                .start_integer( 'reply_size' ).attribute().end() \
                .start_string( 'config' ).attribute().end() \
                .start_integer( 'cores' ).attribute().end()


    def init_client_requests(self):
        gen_clireqsfld = ClientRequests.field( self._gen_durfld )

        self._add_single_result_to_gen( 'client_requests', self._gen_clisumfiles, 'client{cid}.log', gen_clireqsfld, True )

        sum_clireqsfld = ClientRequests.field( self._sum_durfld )
        sum_clireqsfld.name_processor( ResultPrefixProcessor( 'latency_', '' ) )

        self._sumfld.init_field( sum_clireqsfld, 'client_requests' ).attribute().name( 'clireqs' )


    def init_client_issued(self):
        gen_issreqsfld = IssuedRequests.field( self._gen_durfld, 'client_id' )
        gen_issreqsfil = self._file_mapping( IssuedRequestsFileSummary, gen_issreqsfld )
        self._add_file_list_to_gen( 'client_issued', self._gen_clisumfiles, 'client{cid}-clients.log', gen_issreqsfil, True )

        sum_issreqsfld = StatisticalAccumulatorField( RecordFieldSet ).init_cnt().end().init_sum().end().init_mean().end().init_min().end().init_max().end()
        self._sumfld.init_field( sum_issreqsfld, 'client_issued' ).attribute().name( 'cliiss' )


    def init_executed_requests(self):
        gen_execdfld = ExecutedRequests.field( self._gen_amtfld )

        self._add_single_result_to_gen( 'replicas_execd', self._gen_reptskfiles, 'replica{rid}-exectreqs{tno}.log', gen_execdfld )

        sum_execdfld = ExecutedRequests.field( self._sum_amtfld )
        sum_execdfld.name_processor( ResultPrefixProcessor( 'reqs_', 'batches_', '' ) )

        self._sumfld.init_field( self._replicas_field( sum_execdfld ), 'replicas_execd' ).attribute().name( 'execdreqs' )


    def init_consensus_instances(self):
        gen_consfld = ConsensusInstances.field( self._gen_durfld )

        self._add_single_result_to_gen( 'replicas_cons', self._gen_reptskfiles, 'replica{rid}-protinsts{tno}.log', gen_consfld )

        sum_consfld = ConsensusInstances.field( self._sum_durfld )
        sum_consfld.name_processor( ResultPrefixProcessor( 'time_', '' ) )

        self._sumfld.init_field( self._replicas_field( sum_consfld ), 'replicas_cons' ).attribute().name( 'consinsts' )


    def init_applied_checkpoints(self):
        gen_appldfld = AppliedCheckpoints.field( self._gen_amtfld )

        self._add_single_result_to_gen( 'replicas_appld', self._gen_reptskfiles, 'replica{rid}-appldckps{tno}.log', gen_appldfld )

        sum_appldfld = AppliedCheckpoints.field( self._sum_amtfld )
        sum_appldfld.name_processor( ResultPrefixProcessor( 'skippedreqs_', 'chkpnts_', '' ) )

        self._sumfld.init_field( self._replicas_field( sum_appldfld ), 'replicas_appld' ).attribute().name( 'appldckps' )


    def init_processed_requests(self):
        gen_procreqsfld = ProcessedRequests.field( self._gen_durfld )

        self._add_single_result_to_gen( 'replicas_procreqs', self._gen_reptskfiles, 'replica{rid}-repsender{tno}.log', gen_procreqsfld )

        sum_procreqsfld = ProcessedRequests.field( self._sum_durfld )
        sum_procreqsfld.name_processor( ResultPrefixProcessor( 'time_', '' ) )

        self._sumfld.init_field( self._replicas_field( sum_procreqsfld ), 'replicas_procreqs' ).attribute().name( 'procreqs' )


    def init_trans_data(self):
        gen_transfld = TransmittedPacketsResultField( RecordFieldSet ) \
                                .init_sent( self._gen_amtfld ).name( 'sent' ).end() \
                                .init_recv( self._gen_amtfld ).name( 'recv' ).end()
        sum_transfld = TransmittedPacketsResultField( RecordFieldSet ) \
                                .init_sent( self._gen_amtfld ).name( 'sent' ).end() \
                                .init_recv( self._gen_amtfld ).name( 'recv' ).end()
        sum_transfld.name_processor( ResultPrefixProcessor( 'bytes_', 'chunks_', '' ) )

        gen_transdat_file = self._file_mapping( ListedResultFileSummary, gen_transfld )

        self._add_file_list_to_gen( 'clients_trans', self._gen_clifiles, 'client{cid}-trans.log', gen_transdat_file )
        self._sumfld.init_field( self._clients_field( sum_transfld ), 'clients_trans' ).attribute().name( 'trans_clients' )

        self._add_file_list_to_gen( 'replicas_trans', self._gen_repfiles, 'replica{rid}-trans.log', gen_transdat_file )
        self._sumfld.init_field( self._replicas_field( sum_transfld ), 'replicas_trans' ).attribute().name( 'trans' )


    def init_process_resources(self):
        gen_procres_file = ProcessResourcesFileMapping( read_proc=PathProcessor.IsFile, on_error=self._on_file_error )
        self._add_file_list_to_gen( 'clients_procres', self._gen_clifiles, 'resources_proc_client{cid}.log', gen_procres_file )
        self._add_file_list_to_gen( 'replicas_procres', self._gen_repfiles, 'resources_proc_replica{rid}.log', gen_procres_file )

        tcls_rates = DictionaryField( RecordFieldSet ).add_fieldgroup( FloatField, [tcls.name for tcls in JavaThreadClass] )

        sum_procresfld = ProcessResourcesField( RecordFieldSet ) \
                                .init_cpuload( self._sum_ratfld ).end() \
                                .start_systemshare().name( 'cpu_sys_share' ).end() \
                                .init_threadclassrates( tcls_rates ).end() \
                                .start_mem_rss().constant( 0 ).end() \
                                .start_mem_vms().constant( 0 ).end()

        self._sumfld.init_field( self._replicas_field( sum_procresfld ), 'replicas_procres' ).attribute().name( 'procres' )
        self._sumfld.init_field( self._clients_field( sum_procresfld ), 'clients_procres' ).attribute().name( 'procres_clients' )


    def init_host_resources(self):
        gen_hostres_file = HostResourcesFileMapping( read_proc=PathProcessor.IsFile, on_error=self._on_file_error )
        self._add_file_list_to_gen( 'clients_hostres', self._gen_clifiles, 'resources_host_client{cid}.log', gen_hostres_file )
        self._add_file_list_to_gen( 'replicas_hostres', self._gen_repfiles, 'resources_host_replica{rid}.log', gen_hostres_file )

        cputime_shares = DictionaryField( RecordFieldSet ).float(
                                'idle', 'user', 'system', 'iowait', 'irq', 'softirq', 'nice', 'steal', 'guest', 'guest_nice' )

        stash = StatisticalAccumulatorField( RecordFieldSet ).init_cnt().end().init_sum().end().init_mean().end()
        amtsh = AmountResultField( RecordFieldSet ) \
                            .init_duration( IntegerField ).end() \
                            .init_events_per_sec().name( 'per_sec' ).end() \
                            .init_amount_per_sec().name( 'per_sec' ).end() \
                            .init_summary( stash ).integrate().end()
        transfld = TransmittedPacketsResultField( RecordFieldSet ) \
                            .init_sent( amtsh ).name( 'sent' ).end() \
                            .init_recv( amtsh ).name( 'recv' ).end()
        transfld.name_processor( ResultPrefixProcessor( 'bytes_', 'packets_', '' ) )

        nic_stats = DictionaryField( RecordFieldSet ).add_field( transfld, 'eth0', 'eth1', 'eth2', 'eth3', 'eth4' )

        hostresfld = HostResourcesField( RecordFieldSet ) \
                            .start_cpuload_mean( self._sum_ratfld ).end() \
                            .init_cpuload( FloatField ).constant( None ).no_output().end() \
                            .init_cpuload_frequencies( CPULoadFrequencyField( RecordFieldSet, 'cnt_{}' ) ).end() \
                            .init_cputimerates( cputime_shares ).name( 'cpu_share' ).end() \
                            .init_nicstats( nic_stats ).name( 'net' ).end()
        self._sumfld.init_field( self._replicas_field( hostresfld ), 'replicas_hostres' ).attribute().name( 'hostres' )
        self._sumfld.init_field( self._clients_field( hostresfld ), 'clients_hostres' ).attribute().name( 'hostres_clients' )


    def _get_sum_path(self, directory):
        return os.path.join( directory, self._sumfilname )


    def is_result_directory(self, directory):
        return self._resdir_pattern.match( os.path.basename( directory ) )


    def exists_in(self, directory):
        return os.path.exists( self._get_sum_path( directory ) )


    def generate_from(self, directory):
        ressum = ResultsSummary()

        self._genmap.read_to( ressum, directory )

        return ressum


    def read_from(self, directory):
        return self._sumdir.read_from( directory )


    def write_to(self, directory, ressums):
        self._sumdir.write_to( directory, ressums )
        self._repprfdir.write_to( directory, ressums )
        self._represdir.write_to( directory, ressums )
        self._repexcdir.write_to( directory, ressums )



def summarize_results(evaldir, pattern, sumfile, force, globally, data_format_type, cont_on_error):
    datfmt    = data_format_type()
    resmap    = ResultsSummaryMapping( datfmt, sumfile.format( datfmt.default_file_extension ), cont_on_error )
    ressums   = []

    for resdir in sorted( glob.iglob( str( evaldir / pattern ) ) ):
        if not os.path.isdir( resdir ) or not resmap.is_result_directory( resdir ):
            continue

        print( 'Process {}'.format( os.path.basename( resdir ) ) )

        try:
            if resmap.exists_in( resdir ) and not force:
                print( '  --> Summary already exists' )
                if globally:
                    ressums.extend( resmap.read_from( resdir ) )
            else:
                ressum = resmap.generate_from( resdir )
                resmap.write_to( resdir, [ressum] )

                if globally:
                    ressums.append( ressum )
        except Exception as e:
            print( 'Error while parsing {}: {!r}'.format( resdir, e ), file=sys.stderr )
            if not cont_on_error:
                raise e

    if ressums:
        resmap.write_to( evaldir, ressums )



class SumEvalConsl(Consl):
    def __init__(self, progname='sumeval', config=CommandGrammarFamily):
        super().__init__( None )

        self._init_grammar( config, progname )

    def _init_grammar(self, config, progname):
        main  = self._create_sumeval_main( config, progname )
        help_ = self._create_help_fallback( config, progname )

        self._set_grammar( main, help_ )

    def summarize_results(self, args):
        summarize_results( args.eval_dir, args.dir_pattern, args.sum_file, args.force, args.summarize_globally,
                           args.format, args.cont_on_error )


    def _create_sumeval_main(self, config, name):
        main = self._create_main( config, name ) \
                        .call( self.summarize_results )

        mainseq = main.start_choice() \
                        .add_expression( self._create_help_cmd( config ) ) \
                        .start_sequence()

        mainseq.start_variable_option( 'eval-dir', 'd' ) \
                 .add_processor( Converter( Path, True ) ) \
                 .default_value( Path( __file__ ).parent / 'results' )

        mainseq.start_variable_option( 'sum-file', 's' ).default_value( 'summary.{}' )

        mainseq.start_variable_option( 'dir-pattern', 'p' ).default_value( '*' )

        mainseq.start_option( 'format', 't' ) \
                .start_selection( 'format' ) \
                    .alternative( 'csv', store_value=CSV ) \
                    .alternative( 'kv', store_value=lambda: KeyValueFormat( '#' + '-'*42 + '\n' ) ) \
                    .alternative( 'json', store_value=JSON ) \
                    .alternative( 'yaml', store_value=YAML ) \
                    .default_value( CSV ) \

        mainseq.start_flaggroup() \
            .switch( 'summarize-globally', 'do-not-summarize-globally', ('g',), ('G',), default_value=True ) \
            .switch( 'force', 'do-not-force', ('f',), ('F',) ) \
            .switch( 'continue', 'do-not-continue', ('c',), ('C',), dest='cont_on_error' )

        return main
