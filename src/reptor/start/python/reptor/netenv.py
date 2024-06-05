#!/usr/bin/env python3.4

from exprmt.netallocs import EndpointAllocator


class ReptorNetworkEnvironment(EndpointAllocator):
    def __init__(self, netconfig, allocator):
        self._netconfig = netconfig
        self._allocator = allocator

    @property
    def networkconfig(self):
        return self._netconfig

    def all_concrete_hosts(self, system):
        return self._allocator.all_concrete_hosts( system )

    def allocate(self, system):
        return self._allocator.allocate( system )
