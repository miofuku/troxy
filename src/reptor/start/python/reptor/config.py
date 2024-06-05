#!/usr/bin/env python3.4

from collections import OrderedDict
from operator import methodcaller

from plib.networks import Endpoint, Host
from plib.utils.builders import Reference
from translt.fieldelms import OutputDefined
from translt.fields import StringConverter
from translt.fieldsets import RecordFieldSet
from translt.files import StreamToFile
from translt.flat import FlatFormat
from translt.streams import ObjectStreamFormat


EndpointField = StringConverter( Endpoint.parse )


class ReptorSystemConfigMapping:
    def __init__(self, system, fmt=None):
        if fmt is None:
            recmapfmt = FlatFormat( fieldsep='.', truestr='true', falsestr='false', data_read_hook=self._generic_settings_hook )
            fmt       = ObjectStreamFormat.keyvalue( recmapfmt=recmapfmt, datacont=OrderedDict )

        self.system  = system
        self._filmap = StreamToFile( fmt.object_stream( self._create_record( system ) ) )

    def read_from(self, path):
        return self._filmap.read_object_from( path, self.system )

    def write_to(self, path):
        self._filmap.write_object_to( path, self.system )

    @classmethod
    def _generic_settings_hook(cls, recmap, datasrc, retobj, system, used_columns):
        for colname, value in datasrc.items():
            if colname not in used_columns:
                system.generic_settings[ colname ] = value

    @classmethod
    def _create_record(cls, system):
        # TODO: Defaults; e.g. attribute(), decorator
        record = RecordFieldSet().create_composite()

        cls._init_global_config( record, system )
        cls._init_benchmark_config( record, system )
        cls._init_network_config( record, system )
        cls._init_deployment_config( record, system )

        record.start_dictionary( 'generic_settings' ).attribute().integrate().no_input().string()

        return record

    @classmethod
    def _init_global_config(cls, record, system):
        agree = record.start_composite( 'agreement' ).attribute( 'global_settings' ).update()
        agree.start_string( 'protocol' ).attribute( decorator=OutputDefined )
        agree.start_integer( 'replicas' ) \
                .objectgetter( lambda settings: len( system.replicas() ) ) \
                .objectsetter( lambda settings, cnt: system.replicas().set_count( cnt ) ) \
#        agree.start_integer( 'quorum' ).attribute( decorator=OutputDefined )
#        agree.start_integer( 'weak_quorum' ).attribute( decorator=OutputDefined )
#        agree.start_boolean( 'replica_endpoints' ).attribute( decorator=OutputDefined )
        agree.start_integer( 'batchsize_min' ).attribute( decorator=OutputDefined )
        agree.start_integer( 'batchsize_max' ).attribute( decorator=OutputDefined )
#        agree.start_integer( 'checkpoint_interval' ).attribute( decorator=OutputDefined )
#        agree.start_integer( 'in_progress_factor' ).attribute( decorator=OutputDefined )
        agree.start_string( 'checkpoint_mode' ).attribute( decorator=OutputDefined )
#        agree.start_integer( 'checkpoint_threshold' ).attribute( decorator=OutputDefined )
        agree.start_string( 'inst_dist' ).attribute( decorator=OutputDefined )
        agree.start_string( 'rotate' ).attribute( decorator=OutputDefined )
#        agree.start_integer( 'commit_threshold' ).attribute( decorator=OutputDefined )

        client = record.start_composite( 'client' ).attribute( 'global_settings' ).update()
        client.start_string( 'protocol' ).attribute( 'client_prot', OutputDefined )
        client.start_string( 'dist_contacts' ).attribute( decorator=OutputDefined )
        client.start_string( 'repliers' ).attribute( decorator=OutputDefined )

        crypto = record.start_composite( 'crypto' ).attribute( 'global_settings' ).update()
        crypto.start_string( 'message_digest' ).attribute( decorator=OutputDefined )

        crypto.start_composite( 'clients' ) \
                .start_string( 'cert_algo' ).attribute( 'clients_cert_algo', OutputDefined )
        crypto.start_composite( 'replies' ) \
                .start_string( 'cert_algo' ).attribute( 'replies_cert_algo', OutputDefined )
        crypto.start_composite( 'replicas' ) \
                .start_composite( 'strong' ).start_string( 'cert_algo' ).attribute( 'replicas_strong_cert_algo', OutputDefined ).end().end() \
                .start_composite( 'standard' ).start_string( 'cert_algo' ).attribute( 'replicas_standard_cert_algo', OutputDefined ).end().end() \
                .start_string( 'trusted' ).attribute( 'replicas_trusted', OutputDefined )
        crypto.start_composite( 'trinx' ) \
                .start_string( 'library' ).attribute( 'trinx_library', OutputDefined ).end() \
                .start_string( 'enclave' ).attribute( 'trinx_enclave', OutputDefined )

        record.start_composite( 'tbft' ).attribute( 'global_settings' ).update() \
                .start_string( 'troxy' ).attribute( decorator=OutputDefined )

        scheds = record.start_composite( 'schedulers' ).attribute( 'global_settings' ).update()
        scheds.start_composite( 'replica' ) \
                .start_string( 'config' ).attribute( 'replica_scheduler_config', OutputDefined ).end() \
                .start_integer( 'init_number' ).attribute( 'replica_scheduler_count', OutputDefined )
        stages = record.start_composite( 'stages' ).attribute( 'global_settings' ).update()
        stages.start_composite( 'order' ).start_integer( 'number' ).attribute( 'order_stage_count', OutputDefined )

    @classmethod
    def _init_benchmark_config(cls, record, system):
        record.start_composite( 'clients' ) \
                .start_composite( 'benchmark' ).attribute( 'benchmark_settings' ).update().integrate() \
                    .start_integer( 'number' ).attribute( 'client_count', OutputDefined )

        benchgrp = record.start_composite( 'benchmark' )

        bench = benchgrp.start_composite( 'benchmark_settings' ).attribute( 'benchmark_settings' ).update().integrate()

        # Damn PyDev hover bug!
        bench.start_string( 'application' ).attribute( 'servicename', OutputDefined )
        bench.start_string( 'protocol_variant' ).attribute( decorator=OutputDefined )
        bench.start_string( 'benchmark_name' ).attribute( decorator=OutputDefined )
        bench.start_integer( 'request_size' ).attribute( decorator=OutputDefined )
        bench.start_integer( 'reply_size' ).attribute( decorator=OutputDefined )
        bench.start_integer( 'requests_per_client' ).attribute( decorator=OutputDefined )
        bench.start_integer( 'viewchange_interval' ).attribute( decorator=OutputDefined )
#        bench.start_integer( 'state_size' ).attribute( decorator=OutputDefined )
#        bench.start_boolean( 'dummy_request_certs' ).attribute( decorator=OutputDefined )
#        bench.start_boolean( 'verify_replies' ).attribute( decorator=OutputDefined )
#        bench.start_boolean( 'use_request_timeout' ).attribute( decorator=OutputDefined )
#        bench.start_boolean( 'threaded_clients' ).attribute( decorator=OutputDefined )
        bench.start_string( 'process_affinity' ).attribute( decorator=OutputDefined )
        bench.start_string( 'ncores' ).attribute( decorator=OutputDefined )
#
        bench.start_composite( 'client' ) \
                    .start_boolean( 'measure_transmitted_data' ).attribute( 'client_measure_transmitted_data', OutputDefined )

        brep = bench.start_composite( 'replica' )
        brep.start_boolean( 'measure_transmitted_data' ).attribute( 'replica_measure_transmitted_data', OutputDefined )
        brep.start_boolean( 'measure_executed_requests' ).attribute( 'replica_measure_executed_requests', OutputDefined )
        brep.start_boolean( 'measure_applied_checkpoints' ).attribute( 'replica_measure_applied_checkpoints', OutputDefined )
        brep.start_boolean( 'measure_processed_requests' ).attribute( 'replica_measure_processed_requests', OutputDefined )
        brep.start_boolean( 'measure_consensus_instances' ).attribute( 'replica_measure_consensus_instances', OutputDefined )

    @classmethod
    def _init_network_config(cls, record, system):
        net = record.start_composite( 'networks' ).attribute( 'global_settings' ).update()

        net.start_string( 'ssl_algo' ).attribute( decorator=OutputDefined )

        net.start_composite( 'replica' ) \
                    .start_string( 'ssl' ).attribute( 'replicas_ssl', decorator=OutputDefined ).end() \
                    .start_integer( 'addrs' ) \
                        .objectgetter( lambda settings: len( system.replicanetworks() ) ) \
                        .objectsetter( lambda settings, cnt: system.replicanetworks().set_count( cnt ) ).end() \
                    .start_integer( 'client_addrs' ) \
                        .objectgetter( lambda settings: len( system.clientendpoints() ) ) \
                        .objectsetter( lambda settings, cnt: system.clientendpoints().set_count( cnt ) )

        net.start_composite( 'client' ) \
                    .start_string( 'ssl' ).attribute( 'clients_ssl', decorator=OutputDefined )

    @classmethod
    def _init_deployment_config(cls, record, system):
        # Already used in benchmark config.
        record.configure( 'clients' ) \
                    .start_integer( 'hosts' ) \
                        .objectgetter( lambda system: len( system.clients() ) ) \
                        .objectsetter( lambda system, cnt: system.clients().set_count( cnt ) ) \

        addrs = record.start_composite( 'addresses' )

        # TODO: Use direct fields once translators are revised?
        # TODO: Introduce forks and combine replicas and clients? element1 if <cond> else element2
#        class ReplicaAddresses(DirectContainerField):
#            def output_subfields(self, cntxt, proc):
#                if proc.has_assigned_host:
#                    yield None, 'hostname', StringField, proc.host.name
#                    yield None, 'control', EndpointField, proc.endpoints( proc.networks( ControlNetwork )[ 0 ] )[ 0 ].concrete_endpoint
#
#                    for index, net in enumerate( proc.networks( ApplicationNetwork ) ):
#                        yield None, str( index ), EndpointField, proc.endpoints( net )[ 0 ].concrete_endpoint

        repaddrs = addrs.start_namedsequence( 'server' ) \
                        .objectgetter( methodcaller( 'replicas' ) ) \
                        .integrate() \
                        .elements_namepattern( 'server{fieldsep}{}') \
                        .start_composite() \
                            .update()

        repref = Reference()
        repaddrs.store( repref )

        # TODO: Do not write if None.

        def host_getter(proc):
            return proc.host.concrete_host.name if proc.host.concrete_host else None

        def host_setter(cntxt, field, index, proc, hostname):
            proc.host.concrete_host = Host( hostname, None )

        repaddrs.start_string( 'hostname' ) \
                    .objectgetter( host_getter ) \
                    .contextsetter( host_setter )

        def endpoint_getter(cntxt, field, net, proc):
            return proc.endpoints_by_network().endpoints( net )[ 0 ].concrete_endpoint

        def endpoint_setter(cntxt, field, net, proc, value):
            proc.endpoints_by_network().endpoints( net )[ 0 ].concrete_endpoint = value

        repaddrs.init_field( EndpointField, system.controlnetwork ) \
                    .name( 'control' ) \
                    .contextgetter( endpoint_getter ) \
                    .contextsetter( endpoint_setter )

        def endpoint_list_getter(cntxt, field, index, nets):
            return repref.target.get_value( cntxt ).endpoints_by_network().endpoints( nets[ index ] )[ 0 ].concrete_endpoint

        def endpoint_list_setter(cntxt, field, index, nets, value):
            repref.target.get_value( cntxt ).endpoints_by_network().endpoints( nets[ index ] )[ 0 ].concrete_endpoint = value

        repaddrs.start_namedsequence( system.replicanetworks() ) \
                    .objectgetter( lambda proc: proc.system.replicanetworks() ) \
                    .integrate() \
                    .init_field( EndpointField ) \
                        .contextgetter( endpoint_list_getter ) \
                        .contextsetter( endpoint_list_setter )

        repaddrs.start_composite( 'clients' ) \
                .start_namedsequence( system.clientendpoints() ) \
                    .objectgetter( lambda proc: proc.system.clientendpoints() ) \
                    .integrate() \
                    .init_field( EndpointField ) \
                        .contextgetter( endpoint_list_getter ) \
                        .contextsetter( endpoint_list_setter )

        cliaddrs = addrs.start_namedsequence( 'clients' ) \
                        .objectgetter( methodcaller( 'clients' ) ) \
                        .integrate() \
                        .elements_namepattern( 'clients{fieldsep}{}') \
                        .start_composite() \
                            .update()

        cliaddrs.start_string( 'hostname' ) \
                    .objectgetter( host_getter ) \
                    .contextsetter( host_setter )

        cliaddrs.init_field( EndpointField, system.controlnetwork ) \
                    .name( 'control' ) \
                    .contextgetter( endpoint_getter ) \
                    .contextsetter( endpoint_setter )

        cliaddrs.init_field( EndpointField, system.clientnetwork ) \
                    .name( '0' ) \
                    .contextgetter( endpoint_getter ) \
                    .contextsetter( endpoint_setter )
