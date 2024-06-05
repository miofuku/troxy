#!/usr/bin/env python3.4

from exprmt.netallocs import LocalEndpointAllocator
from reptor.netenv import ReptorNetworkEnvironment
from plib.networks import NetworkConfig


def load_netenv():
    return ReptorNetworkEnvironment( NetworkConfig.Local, LocalEndpointAllocator.client_servers( cliportblock=5000 ) )
