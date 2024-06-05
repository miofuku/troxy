#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod

from consl.cmds import CommandGrammarFamily, CommandExpression
from plib.networks import NetworkConfigError


class NetworkConfigCLI(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def networkconfig(self):
        ...

    def print_all_groups(self):
        for group in self._networkconfig_checked.hostgroups:
            print( '{:15} {}'.format( group.name, ', '.join( [h.name for h in group.hosts] ) ) )

    def print_group(self, hostgroup):
        for host in hostgroup.hosts:
            self.print_host( host )

    def print_all_hosts(self):
        for _, host in sorted( self._networkconfig_checked.hosts_by_fullname.items() ):
            self.print_host( host )

    def print_host(self, host):
        print( '{:15}'.format( host.fullname ) )
        for net in self._networkconfig_checked.networks:
            if net in host.ipaddrs_by_network:
                for ip in host.ipaddrs_by_network[ net ]:
                    print( '    {:15} {!s:15} ({})'.format( ip.network.name, ip.hostaddr, host.get_interface_by_ipaddr( ip ).name ) )

    def print_all_nets(self):
        for net in self._networkconfig_checked.networks:
            print( '{:15} {}'.format( net.name, net.address ) )

    @property
    def _networkconfig_checked(self):
        if self.networkconfig is None:
            raise ValueError( 'Network configuration has not been initialized!' )
        else:
            return self.networkconfig


class NetworkConfigConslCLI(NetworkConfigCLI):
    __slots__ = ()

    def process_hostgroup(self, cntxt, expr, value, error_level):
        try:
            return self._networkconfig_checked.get_hostgroup( value )
        except NetworkConfigError as err:
            raise ValueError( err )

    def process_host(self, cntxt, expr, value, error_level):
        try:
            return self._networkconfig_checked.get_host( value )
        except NetworkConfigError as err:
            raise ValueError( err )

    def process_network(self, cntxt, expr, value, error_level):
        try:
            return self._networkconfig_checked.get_network( value )
        except NetworkConfigError as err:
            raise ValueError( err )


class ShowNetworkConfig(CommandExpression):
    def __init__(self, cli, name='show', config=CommandGrammarFamily):
        super().__init__( name, config )

        self.cli = cli

        self._init_cmd()

    def _init_cmd(self):
        self.abstract( 'Show network configuration' ) \
            .start_choice() \
                .add_expression( self._create_showgroups_cmd() ) \
                .add_expression( self._create_showhosts_cmd() ) \
                .add_expression( self._create_shownets_cmd() ) \
                .end()

    def _create_showgroups_cmd(self):
        return self._config.create_command( 'group' ) \
                            .abstract( 'Show known host groups' ) \
                            .start_choice() \
                                .start_variable( 'hostgroup' ).add_processor( self.cli.process_hostgroup ).paramcall( self.cli.print_group ).end() \
                                .start_sequence().paramcall( self.cli.print_all_groups ).start_constant( '*' ).optional().end().end() \
                                .end()

    def _create_showhosts_cmd(self):
        return self._config.create_command( 'host' ) \
                                .abstract( 'Show known hosts' ) \
                                .start_choice() \
                                    .start_variable( 'host' ).add_processor( self.cli.process_host ).paramcall( self.cli.print_host ).end() \
                                    .start_sequence().paramcall( self.cli.print_all_hosts ).start_constant( '*' ).optional().end().end() \
                                    .end()

    def _create_shownets_cmd(self):
        return self._config.create_command( 'nets' ) \
                                .abstract( 'Show known networks' ) \
                                .paramcall( self.cli.print_all_nets ) \
                                .start_constant( '*' ).optional().end()
