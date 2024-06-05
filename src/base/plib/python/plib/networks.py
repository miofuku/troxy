#!/usr/bin/env python3.4

#from ipaddress import IPv4Network as IPv4NetworkAddress
from abc import ABCMeta
from collections import namedtuple
from collections.abc import MutableSequence
import ipaddress
import socket


# TODO: Explorer for network topologies and system information like CPUs, memory, OS etc. (generate network configurations)
class Addressable(metaclass=ABCMeta):
    __slots__ = ()

    def __str__(self):
        ...

Addressable.register( ipaddress.IPv4Address )
Addressable.register( ipaddress.IPv6Address )


class Network:
    Loopback = None

    def __init__(self, name, address, parse_addr=True):
        self.name    = name
        self.address = ipaddress.ip_network( address ) if parse_addr else address

    def __str__(self):
        return '{}={}'.format( self.name, self.address )

Network.Loopback = Network( '<loopback>', '127.0.0.0/8' )



class Endpoint(namedtuple( 'Endpoint', 'address port' )):
    __slots__ = ()

    @classmethod
    def parse(cls, str_):
        hostaddr, port = str_.rsplit( ':', 2 )

        return cls( ipaddress.ip_address( hostaddr ), int( port ) )

    def __str__(self):
        return '{}:{}'.format( self.address, self.port )



class QualifiedIPAddress:
    Loopback = None

    def __init__(self, network, hostaddr, parse_addr=True):
        self.network  = network
        self.hostaddr = ipaddress.ip_address( hostaddr ) if parse_addr else hostaddr

        if self.hostaddr not in network.address:
            raise ValueError( '{} is not in {}'.format( self.hostaddr, network.address ) )

    def create_endpoint(self, port):
        return Endpoint( self.hostaddr, port )

    @property
    def version(self):
        return self.hostaddr.version

    @property
    def max_prefixlen(self):
        return self.hostaddr.max_prefixlen

    @property
    def compressed(self):
        return self.hostaddr.compressed

    @property
    def exploded(self):
        return self.hostaddr.exploded

    @property
    def packed(self):
        return self.hostaddr.packed

    @property
    def is_multicast(self):
        return self.hostaddr.is_multicast

    @property
    def is_private(self):
        return self.hostaddr.is_private

    @property
    def is_global(self):
        return self.hostaddr.is_global

    @property
    def is_unspecified(self):
        return self.hostaddr.is_unspecified

    @property
    def is_reserved(self):
        return self.hostaddr.is_reserved

    @property
    def is_loopback(self):
        return self.hostaddr.is_loopback

    @property
    def is_link_local(self):
        return self.hostaddr.is_link_local

    def __str__(self):
        return '{}/{}'.format( self.hostaddr, self.network.name )

QualifiedIPAddress.Loopback = QualifiedIPAddress( Network.Loopback, '127.0.0.1' )



class _IPConnected:
    def __init__(self, name):
        self.name               = name
        self.ipaddrs            = set()
        self.ipaddrs_by_network = {}

    def _add_ipaddr(self, ipaddr):
        self.ipaddrs.add( ipaddr )
        self.ipaddrs_by_network.setdefault( ipaddr.network, [] ).append( ipaddr )


    def get_ipaddrs_by_network(self, network):
        if network in self.ipaddrs_by_network:
            return self.ipaddrs_by_network[ network ]
        else:
            raise NetworkConfigError( '{} is not connected to {}.'.format( self.name, network.name ) )



class NetworkInterface(_IPConnected):
    Loopback = None

    def __init__(self, name, ipaddrs=None):
        super().__init__( name )

        if ipaddrs:
            for addr in ipaddrs:
                self._add_ipaddr( addr )

    def __str__(self):
        return self.name

NetworkInterface.Loopback = NetworkInterface( '<loopback>', (QualifiedIPAddress.Loopback,) )


# TODO: Aliases
class Host(_IPConnected, Addressable):
    Local = None

    def __init__(self, name, domain, netifs=None):
        super().__init__( name )
        self.domain = domain
        self.netifs = netifs or []
        self.interface_by_ipaddr = {}

        for netif in self.netifs:
            for addr in netif.ipaddrs:
                self._add_ipaddr( addr )
                self.interface_by_ipaddr[ addr ] = netif

    def get_interface_by_ipaddr(self, ipaddr):
        if ipaddr in self.interface_by_ipaddr:
            return self.interface_by_ipaddr[ ipaddr ]
        else:
            raise NetworkConfigError( '{} is not an address of {}.'.format( ipaddr, self.name ) )

    @property
    def is_local(self):
        return self is Host.Local or self.name==Host.Local.name or self.fullname==socket.getfqdn()

    @property
    def fullname(self):
        return self.name + '.' + self.domain if self.domain is not None else self.name

    def __str__(self):
        return self.fullname

Host.Local = Host( 'localhost', None, (NetworkInterface.Loopback,) )



class HostGroup(MutableSequence):
    def __init__(self, name, hosts=None):
        self.name  = name
        self.hosts = hosts or []

    def __getitem__(self, key):
        return self.hosts[ key ]

    def __setitem__(self, key, value):
        self.hosts[ key ] = value

    def __delitem__(self, key):
        del self.hosts[ key ]

    def __len__(self):
        return len( self.hosts )

    def insert(self, i, x):
        self.hosts.insert( i, x )

    def __str__(self):
        return self.name



class NetworkConfigError(Exception):
    pass



class NetworkConfig:
    Local = None

    def __init__(self):
        self.networks           = []
        self.networks_by_name   = {}
        self.hostgroups         = []
        self.hostgroups_by_name = {}
        self.hosts_by_fullname  = {}
        self.hosts_by_name      = {}

    def hosts(self):
        hs = set()

        for hostgroup in self.hostgroups:
            for host in hostgroup:
                if host not in hs:
                    hs.add( host )
                    yield host


    def add_network(self, network):
        assert network.name not in self.networks_by_name

        self.networks.append( network )
        self.networks_by_name[ network.name ] = network


    def add_host(self, host):
        assert host.fullname not in self.hosts_by_fullname

        self.hosts_by_fullname[ host.fullname ] = host
        self.hosts_by_name.setdefault( host.name, [] ).append( host )


    def add_hostgroup(self, hostgroup):
        assert hostgroup.name not in self.hostgroups_by_name

        self.hostgroups.append( hostgroup )
        self.hostgroups_by_name[ hostgroup.name ] = hostgroup

        for host in hostgroup:
            assert host.fullname not in self.hosts_by_fullname or host==self.hosts_by_fullname[ host.fullname ]

            if host.fullname not in self.hosts_by_fullname:
                self.add_host( host )


    def get_network(self, netname):
        if netname in self.networks_by_name:
            return self.networks_by_name[ netname ]
        else:
            raise NetworkConfigError( 'Unknown network ' + netname )


    def get_hostgroup(self, hgname):
        if hgname in self.hostgroups_by_name:
            return self.hostgroups_by_name[ hgname ]
        else:
            raise NetworkConfigError( 'Unknown host group ' + hgname )


    def get_host(self, hname):
        if hname in self.hosts_by_fullname:
            return self.hosts_by_fullname[ hname ]
        elif hname not in self.hosts_by_name:
            raise NetworkConfigError( 'Unknown host ' + hname )
        elif len( self.hosts_by_name[ hname ] )>1:
            raise NetworkConfigError( 'Host name ambiguous: ' + ', '.join( [h.fullname for h in self.hosts_by_name[ hname ]] ) )
        else:
            return self.hosts_by_name[ hname ][ 0 ]


    def get_hostgroup_or_host(self, name):
        return self.hostgroups_by_name[ name ] if name in self.hostgroups_by_name else (self.get_host( name ),)


NetworkConfig.Local = NetworkConfig()
NetworkConfig.Local.add_network( Network.Loopback )
NetworkConfig.Local.add_host( Host.Local )
