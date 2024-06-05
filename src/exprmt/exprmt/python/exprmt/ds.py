#!/usr/bin/env python3.4

from exprmt.system import ContainedEntities, SystemObject, SystemEntityGroup, AdjustableEntityGroup, \
    EntityBlocked, System


class ProcessesOfHost(ContainedEntities):
    def __init__(self, host):
        super().__init__( SystemProcess )

        self._host = host

    def _predicate(self, x):
        return x.host==self._host

    def processtypes(self):
        return self._entitytypes()

    def processes(self, proctype):
        return self._entities.get( proctype, () )

    @property
    def host(self):
        return self._host


class SystemHost(SystemObject):
    category = None

    def __init__(self):
        super().__init__()

        self._procs = ProcessesOfHost( self )

        self.concrete_host = None

    def _initialize(self, system, cntxt):
        super()._initialize( system, cntxt )

        self._procs.register( system )

    def processes(self):
        return self._procs

    def dependent_entities(self):
        yield from self._procs
        yield self._procs

SystemHost.category = SystemHost


class NetworksForProcess(ContainedEntities):
    def __init__(self, proc):
        super().__init__( SystemNetwork )

        self._proc = proc

    def _initialize(self, system, cntxt):
        super()._initialize( system, cntxt )

        for net in system.entities_of_abstract_type( SystemNetwork ):
            if self._proc in net.processes():
                self._add_entity( net )

    def _predicate(self, x):
        return self._proc in x.processes()

    def networktypes(self):
        return self._entitytypes()

    def of_type(self, nettype):
        return self._entities.get( nettype, () )

    @property
    def process(self):
        return self._proc


class EndpointsOfProcess(ContainedEntities):
    def __init__(self, proc):
        super().__init__( SystemEndpoint )

        self._proc = proc

    def _predicate(self, x):
        return x.process==self._proc

    def endpointtypes(self):
        return self._entitytypes()

    def of_type(self, eptype):
        return self._entities.get( eptype, () )

    @property
    def process(self):
        return self._proc


class EndpointsOfProcessByNetwork(ContainedEntities):
    def __init__(self, proc):
        super().__init__( SystemEndpoint )

        self._proc = proc

    def _predicate(self, x):
        return x.process==self._proc

    def _key(self, ep):
        return ep.network

    def networks(self):
        return self._entitytypes()

    def endpoints(self, net):
        return self._entities.get( net, () )

    def ordered(self):
        nets     = self._proc.networks()
        nettypes = nets.networktypes()

        for nettype in self.system.subtypes_of_type( SystemNetwork ).ordered():
            if nettype in nettypes:
                for net in nets.of_type( nettype ):
                    yield from self._entities[ net ]

    def __iter__(self):
        for eps in self._entities.values():
            yield from eps

    @property
    def process(self):
        return self._proc


class SystemProcess(SystemObject):
    category = None

    def __init__(self, host):
        super().__init__()

        self._host = host
        self._networks   = NetworksForProcess( self )
        self._endpoints  = EndpointsOfProcess( self )
        self._eps_by_net = EndpointsOfProcessByNetwork( self )

    def _initialize(self, system, cntxt):
        assert self._host.system==system
        super()._initialize( system, cntxt )

        self._networks.register( system )
        self._endpoints.register( system )
        self._eps_by_net.register( system )

    def networks(self):
        return self._networks

    def endpoints(self):
        return self._endpoints

    def endpoints_by_network(self):
        return self._eps_by_net

    def dependent_entities(self):
        yield from self._endpoints
        yield from (self._networks, self._endpoints, self._eps_by_net)

    @property
    def host(self):
        return self._host

SystemProcess.category = SystemProcess


class EndpointsForNetwork(SystemEntityGroup):
    def __init__(self, net):
        super().__init__()

        self._net = net

    def __contains__(self, x):
        return isinstance( x, SystemEndpoint ) and x.network==self._net

    def __len__(self):
        c = 0

        for proc in self._net.processes():
            c += len( proc.endpoints_by_network().endpoints( self._net ) )

        return c

    def ordered(self):
        # It is assumed that all endpoints of a process in a network are of the same type.
        for proc in self._net.processes().ordered():
            yield from self.of_process( proc )

    def __iter__(self):
        for proc in self._net.processes():
            yield from self.of_process( proc )

    def of_processtype(self, proctype):
        for proc in self._net.processes():
            if issubclass( proc.type, proctype ):
                yield from self.of_process( proc )

    def of_process(self, proc):
        return proc.endpoints_by_network().endpoints( self._net )

    @property
    def network(self):
        return self._net


# Networks determine the type and the lifecyle of (their) endpoints.
class SystemNetwork(SystemObject):
    category = None

    def __init__(self, procs, group=None):
        super().__init__()

        self._procs = procs
        self._group = group
        self._endpoints = EndpointsForNetwork( self )

    def _initialize(self, system, cntxt):
        super()._initialize( system, cntxt )

        self._endpoints.register( system )

        for proc in self._procs:
            self._create_endpoints( proc )

        self._subscribe_lifecycle( SystemProcess, self._is_process_in_group, self._process_registered, self._process_removed )

    def _dispose(self, disset):
        if self._group:
            self._group._dispose_entity( self, disset )
        super()._dispose( disset )

    def _dispose_entity(self, ep, disset):
        if self not in disset and ep.process not in disset:
            raise EntityBlocked( ep, self )

    def _is_process_in_group(self, proc):
        return proc in self._procs

    def _create_endpoints(self, proc):
        SystemEndpoint( self, proc ).register( self.system )

    def _remove_endpoints(self, proc):
        pass

    def _process_registered(self, sub, event):
        self._create_endpoints( event.entity )

    def _process_removed(self, sub, event):
        self._remove_endpoints( event.entity )

    def processes(self):
        return self._procs

    def endpoints(self):
        return self._endpoints

    def group(self):
        return self._group

    def dependent_entities(self):
        yield from self._endpoints
        yield self._endpoints

SystemNetwork.category = SystemNetwork


class SystemNetworkGroup(AdjustableEntityGroup):
    def __init__(self, nettype, procs):
        super().__init__()

        self._nettype = nettype
        self._procs   = procs

    def _create_entity(self, i):
        return self._nettype( self._procs, self )

    @property
    def networktype(self):
        return self._nettype

    @property
    def processes(self):
        return self._procs


class SystemEndpoint(SystemObject):
    category = None

    def __init__(self, net, proc):
        assert proc in net.processes()

        super().__init__()

        self._network = net
        self._process = proc

        # TODO: System endpoints sharing a concrete hosts also share the concrete endpoint if they are in the same entanglement.
#        self.entanglement      = None
        self.concrete_endpoint = None

    def _initialize(self, system, cntxt):
        assert self._process.system==system and self._network.system==system
        super()._initialize( system, cntxt )

    def _dispose(self, disset):
        self._network._dispose_entity( self, disset )
        super()._dispose( disset )

    @property
    def process(self):
        return self._process

    @property
    def host(self):
        return self._process.host

    @property
    def network(self):
        return self._network

SystemEndpoint.category = SystemEndpoint


class DistributedSystem(System):
    def _init_default_entitytypes(self):
        super()._init_default_entitytypes()

        self.add_entitytype( ProcessesOfHost, NetworksForProcess, EndpointsOfProcess, EndpointsOfProcessByNetwork, EndpointsForNetwork )

    def assign_concrete_objects(self, alloc):
        for host in self.entities_of_abstract_type( SystemHost ):
            host.concrete_host = alloc.concrete_host( host )
            for proc in host.processes():
                for ep in proc.endpoints():
                    ep.concrete_endpoint = alloc.concrete_endpoint( ep )


class ControlNetwork(SystemNetwork):
    pass


class ServiceNetwork(SystemNetwork):
    pass


class ServiceProcess(SystemProcess):
    pass

class Server(ServiceProcess):
    pass


class Client(ServiceProcess):
    pass


class AdjustableProcessGroup(AdjustableEntityGroup):
    def _get_host(self, i):
        return SystemHost().register( self._system )

    def _dependent_of_entity(self, proc):
        return (proc.host,)


class Replica(Server):
    def __init__(self, group, no, host):
        super().__init__( host )

        self._group  = group
        self._number = no

    def _dispose(self, disset):
        self._group._dispose_entity( self, disset )
        super()._dispose( disset )

    @property
    def number(self):
        return self._number

    @property
    def group(self):
        return self._group

    def dependent_entities(self):
        yield from super().dependent_entities()
        yield from self._group._dependent_of_entity( self )


class ReplicaGroup(AdjustableProcessGroup):
    def __init__(self, reptype):
        super().__init__()

        self._reptype = reptype

    def _create_entity(self, i):
        return self._reptype( self, i, self._get_host( i ) )

    @property
    def replicatype(self):
        return self._reptype


class BenchmarkClient(Client):
    def __init__(self, group, no, host):
        super().__init__( host )

        self._group  = group
        self._number = no

    def _dispose(self, disset):
        self._group._dispose_entity( self, disset )
        super()._dispose( disset )

    @property
    def number(self):
        return self._number

    @property
    def group(self):
        return self._group

    def dependent_entities(self):
        yield from super().dependent_entities()
        yield from self._group._dependent_of_entity( self )


class BenchmarkClientGroup(AdjustableProcessGroup):
    def __init__(self, clitype):
        super().__init__()

        self._clitype = clitype

    def _create_entity(self, i):
        return self._clitype( self, i, self._get_host( i ) )

    @property
    def clienttype(self):
        return self._clitype
