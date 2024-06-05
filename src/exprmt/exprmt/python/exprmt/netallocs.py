#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod

from exprmt.ds import SystemProcess, SystemNetwork, Server, Client, ControlNetwork, ServiceNetwork
from plib.networks import Host, Network, QualifiedIPAddress
from plib.utils.builders import FluidMixin


class EndpointAllocator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def all_concrete_hosts(self, system):
        ...

    @abstractmethod
    def allocate(self, system):
        ...


class EndpointAllocation:
    @classmethod
    def __init__(self, system, hostalloc, netalloc, addralloc, portalloc):
        self.system            = system
        self.host_allocator    = hostalloc
        self.network_allocator = netalloc
        self.address_allocator = addralloc
        self.port_allocator    = portalloc

        self.typemappings = {}

    def typemapping(self, category, systypes):
        idx = frozenset( systypes )
        tm  = self.typemappings.get( idx, None )

        if tm is None:
            tm = self.typemappings[ idx ] \
               = BaseTypeEntityMapping().calc_mappings( self.system, category, idx )

        return tm

    def concrete_host(self, host):
        return self.host_allocator.concrete_host( self, host )

    def concrete_network(self, sysnet):
        return self.network_allocator.concrete_network( self, sysnet )

    def concrete_endpoint(self, sysep):
        addr = self.address_allocator.address( self, sysep )
        port = self.port_allocator.port( self, sysep )
        return addr.create_endpoint( port )


class HostAllocator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def all_concrete_hosts(self, system):
        ...

    @abstractmethod
    def concrete_host(self, alloc, host):
        ...


class NetworkAllocator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def number_for_concrete_network(self, alloc, sysnet):
        ...

    @abstractmethod
    def concrete_network(self, alloc, sysnet):
        ...


class AddressAllocator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def address(self, alloc, sysep):
        ...


class PortAllocator(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def port(self, alloc, sysep):
        ...


class RoundRobinAllocator:
    __slots__ = ()

    @classmethod
    def _select_roundrobin(cls, lst, i):
        return lst[ i % len( lst ) ]


class SystemEntityMapping(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def get_type_and_number(self, ent):
        ...


class BaseTypeEntityMapping(SystemEntityMapping):
    def __init__(self):
        self.entitymappings = {}

    def set_mapping(self, ent, basetype, number):
        self.entitymappings[ ent ] = basetype, number

    def get_type_and_number(self, ent):
        return self.entitymappings[ ent ]

    def calc_mappings(self, system, category, basetypes, systypes=None):
        remtypes = list( systypes or system.subtypes_of_type( category ) )

        for basetype in basetypes:
            filtypes = [t for t in remtypes if issubclass( t, basetype )]
            remtypes = [t for t in remtypes if t not in filtypes]
            self._calc_single_mapping( system, category, basetype, filtypes )

#        if remtypes:
#            raise ValueError( 'No base type found for {}.'.format( remtypes ) )

        return self

    def _calc_single_mapping(self, system, category, basetype, systypes):
        curno = 0
        for systype in system.subtypes_of_type( category ).ordered():
            if systype in systypes:
                for ent in system.entities_of_specific_type( systype ).ordered():
                    self.set_mapping( ent, basetype, curno )
                    curno += 1


class RoundRobinProcessOrientedHostAllocator(HostAllocator, RoundRobinAllocator):
    def __init__(self):
        self._proctypes_to_hosts = {}
        self._syshosts_to_hosts  = {}

    def all_concrete_hosts(self, system):
        all_ = set()

        for hosts in self._proctypes_to_hosts.values():
            all_.update( hosts )

        return all_

    def set_hostmapping(self, proctype, hosts):
        self._proctypes_to_hosts[ proctype ] = hosts

    def clear(self):
        self._proctypes_to_hosts.clear()
        self._syshosts_to_hosts.clear()

    def concrete_host(self, alloc, host):
        if host in self._syshosts_to_hosts:
            return self._syshosts_to_hosts[ host ]
        else:
            assert( len( host.processes() ) )
            # TODO: What if multiple processes are placed on a single host? Server and Monitor -> Server is decisive.
            #       Check contradicting assignments? Server -> hostgroup1, Monitor -> None, Client -> hostgroup2!=hostgroup1
            proc = next( iter( host.processes().ordered() ) )
            proctype, procno = alloc.typemapping( SystemProcess, self._proctypes_to_hosts.keys() ).get_type_and_number( proc )
            host = self._syshosts_to_hosts[ host ] \
                 = self._select_roundrobin( self._proctypes_to_hosts[ proctype ], procno )
            return host


class SingleHostAllocator(HostAllocator):
    def __init__(self, host):
        self._host = host

    def all_concrete_hosts(self, system):
        return (self._host,)

    def concrete_host(self, alloc, host):
        return self._host


class RoundRobinNetworkAllocator(NetworkAllocator, RoundRobinAllocator):
    def __init__(self):
        self._nets = {}

    def set_networkmapping(self, nettype, nets):
        self._nets[ nettype ] = nets

    def clear(self):
        self._nets.clear()

    def number_for_concrete_network(self, alloc, sysnet):
        nettype, netno = alloc.typemapping( SystemNetwork, self._nets.keys() ).get_type_and_number( sysnet )
        return netno//len( self._nets[ nettype ] )

    def concrete_network(self, alloc, sysnet):
        nettype, netno = alloc.typemapping( SystemNetwork, self._nets.keys() ).get_type_and_number( sysnet )
        return self._select_roundrobin( self._nets[ nettype ], netno )


class SingleNetworkAllocator(NetworkAllocator):
    def __init__(self, net):
        self._net = net

    def number_for_concrete_network(self, alloc, sysnet):
        return alloc.typemapping( SystemNetwork, SystemNetwork ).get_type_and_number( sysnet )

    def concrete_network(self, alloc, sysnet):
        return self._net


class RoundRobinAddressAllocator(AddressAllocator, RoundRobinAllocator):
    def __init__(self, hostalloc, netalloc):
        self.host_allocator    = hostalloc
        self.network_allocator = netalloc

    def address(self, alloc, sysep):
        host  = self.host_allocator.concrete_host( alloc, sysep.host )
        net   = self.network_allocator.concrete_network( alloc, sysep.network )
        block = self.network_allocator.number_for_concrete_network( alloc, sysep.network )
        return self._select_roundrobin( host.get_ipaddrs_by_network( net ), block )


class SingleAddressAllocator(AddressAllocator):
    def __init__(self, addr):
        self._addr = addr

    def address(self, alloc, sysep):
        return self._addr


class ClusteredPortAllocator(PortAllocator):
    def __init__(self):
        self._ports = {}

    def set_portmapping(self, proctype, portbase, max_ports_per_proc):
        self._ports[ proctype ] = portbase, max_ports_per_proc

    def clear(self):
        self._ports.clear()

    def port(self, alloc, sysep):
        proctype, procno = alloc.typemapping( SystemProcess, self._ports.keys() ).get_type_and_number( sysep.process )

        base, block = self._ports[ proctype ]
        # TODO: Looks not very efficient.
        eppn        = list( sysep.process.endpoints_by_network().ordered() ).index( sysep )
        return base + block*procno + eppn


class LocalEndpointAllocator(EndpointAllocator):
    @classmethod
    def processes(cls, portbase=1000, portblock=100):
        portalloc = ClusteredPortAllocator()
        portalloc.set_portmapping( SystemProcess, portbase, portblock )

        return cls( portalloc )

    @classmethod
    def client_servers(cls, srvportbase=10000, srvportblock=100, cliportbase=20000, cliportblock=100):
        portalloc = ClusteredPortAllocator()
        portalloc.set_portmapping( Server, srvportbase, srvportblock )
        portalloc.set_portmapping( Client, cliportbase, cliportblock )

        return cls( portalloc )

    def __init__(self, portalloc):
        self.port_allocator = portalloc

    def all_concrete_hosts(self, system):
        return (Host.Local,)

    def allocate(self, system):
        return EndpointAllocation( system, self._get_hostalloc(), self._get_netalloc(), self._get_addralloc(),
                                   self.port_allocator )

    _hostalloc = None
    _netalloc  = None
    _addralloc = None

    @classmethod
    def _get_hostalloc(cls):
        if cls._hostalloc is None:
            cls._hostalloc = SingleHostAllocator( Host.Local )
        return cls._hostalloc

    @classmethod
    def _get_netalloc(cls):
        if cls._netalloc is None:
            cls._netalloc = SingleNetworkAllocator( Network.Loopback )
        return cls._netalloc

    @classmethod
    def _get_addralloc(cls):
        if cls._addralloc is None:
            cls._addralloc = SingleAddressAllocator( QualifiedIPAddress.Loopback )
        return cls._addralloc


# TODO: configure_hosts() etc. for own configuration.
class NetConfigEndpointAllocator(EndpointAllocator, FluidMixin):
    def __init__(self, netconfig):
        self.netconfig  = netconfig
        self._addralloc = RoundRobinAddressAllocator( None, None )

        self.global_config()

    def single_config(self, hostname, netname, portbase=None, portblock=None):
        return self.single_host( hostname ).single_network( netname ).global_ports( portbase, portblock )

    def global_config(self, hostnames=None, netnames=None, portbase=None, portblock=None):
        return self.global_hosts( hostnames ).global_networks( netnames ).global_ports( portbase, portblock )

    def client_server_config(self, clinames, srvnames, cliportbase=None, cliportblock=None,
                             srvportbase=None, srvportblock=None):
        return self.client_server_hosts( clinames, srvnames ) \
                   .client_server_ports( cliportbase, cliportblock, srvportbase, srvportblock )

    def single_host(self, hostname):
        self._hostalloc = SingleHostAllocator( self.netconfig.get_host( hostname ) )
        self._addralloc.host_allocator = self._hostalloc
        return self

    def global_hosts(self, hostnames):
        self._hostalloc = RoundRobinProcessOrientedHostAllocator()
        self._hostalloc.set_hostmapping( SystemProcess, self._get_hosts( hostnames ) )
        self._addralloc.host_allocator = self._hostalloc
        return self

    def client_server_hosts(self, clinames, srvnames):
        self._hostalloc = RoundRobinProcessOrientedHostAllocator()
        self._hostalloc.set_hostmapping( Client, self._get_hosts( clinames ) )
        self._hostalloc.set_hostmapping( Server, self._get_hosts( srvnames ) )
        self._addralloc.host_allocator = self._hostalloc
        return self

    def single_network(self, netname):
        self._netalloc = SingleNetworkAllocator( self.netconfig.get_network( netname ) )
        self._addralloc.network_allocator = self._netalloc
        return self

    def global_networks(self, netnames):
        self._netalloc = RoundRobinNetworkAllocator()
        self._netalloc.set_networkmapping( SystemNetwork, self._get_nets( netnames ) )
        self._addralloc.network_allocator = self._netalloc
        return self

    def control_service_networks(self, ctrlnetnames=None, appnetnames=None):
        self._netalloc = RoundRobinNetworkAllocator()
        self._netalloc.set_networkmapping( ControlNetwork, self._get_nets( ctrlnetnames ) )
        self._netalloc.set_networkmapping( ServiceNetwork, self._get_nets( appnetnames ) )
        self._addralloc.network_allocator = self._netalloc
        return self

    def global_ports(self, portbase=None, portblock=None):
        baseport = 10000 if portbase is None else portbase
        portblock = 100 if portblock is None else portblock

        self._portalloc = ClusteredPortAllocator()
        self._portalloc.set_portmapping( SystemProcess, baseport, portblock )
        return self

    def client_server_ports(self, cliportbase=None, cliportblock=None, srvportbase=None, srvportblock=None):
        cliportbase = 20000 if cliportbase is None else cliportbase
        cliportblock = 100 if cliportblock is None else cliportblock
        srvportbase = 10000 if srvportbase is None else srvportbase
        srvportblock = 100 if srvportblock is None else srvportblock

        self._portalloc = ClusteredPortAllocator()
        self._portalloc.set_portmapping( Client, cliportbase, cliportblock )
        self._portalloc.set_portmapping( Server, srvportbase, srvportblock )
        return self

    def _get_hosts(self, hostnames):
        if not hostnames:
            return list( self.netconfig.hosts() )
        else:
            hosts = []
            for hn in hostnames:
                hosts.extend( self.netconfig.get_hostgroup_or_host( hn ) )
            return hosts

    def _get_nets(self, netnames):
        if not netnames:
            return list( self.netconfig.networks )
        else:
            return [self.netconfig.get_network( nn ) for nn in netnames]

    def all_concrete_hosts(self, system):
        return self._hostalloc.all_concrete_hosts( system )

    def allocate(self, system):
        return EndpointAllocation( system, self._hostalloc, self._netalloc, self._addralloc, self._portalloc )
