#!/usr/bin/env python3.4

from exprmt.netallocs import NetConfigEndpointAllocator
from netconfig_ibr import IBRNetworkConfig
from reptor.netenv import ReptorNetworkEnvironment


def load_netenv():
    netconfig = IBRNetworkConfig()
    epalloc   = NetConfigEndpointAllocator( netconfig ) \
                        .client_server_config( ('cloud1', 'cloud2'), ('beagle1', 'beagle2'), cliportblock=5000 ) \
                        .control_service_networks( ('public',), ('private0', 'rdma') )
    return ReptorNetworkEnvironment( netconfig, epalloc )
