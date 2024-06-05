#!/usr/bin/env python3.4

import start

from montr.cli import ResourceMonitorConsl
from reptor.monitor import ReptorResourceMonitor


if __name__=='__main__':
    ResourceMonitorConsl( ReptorResourceMonitor ).evaluate()
