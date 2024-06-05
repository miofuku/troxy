#!/usr/bin/env python3.4

from exprmt.netallocs import NetConfigEndpointAllocator
from netconfig_ibr import IBRNetworkConfig
from reptor.netenv import ReptorNetworkEnvironment


def load_netenv():
    netconfig = IBRNetworkConfig()
    epalloc   = NetConfigEndpointAllocator( netconfig ) \
                        .client_server_config( ('dsgxsc',), ('dsgxss',), cliportblock=5000 ) \
                        .control_service_networks( ('public',), ('dsgxnet1', 'dsgxnet2', 'dsgxnet3', 'dsgxnet4') )
    return ReptorNetworkEnvironment( netconfig, epalloc )
