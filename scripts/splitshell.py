#!/usr/bin/env python3.4

import start

import netconfig_ibr as netconfig
from exprmt.cli.splitshell import SplitShell


if __name__=='__main__':
    SplitShell( netconfig.load_network_config() ).evaluate()
