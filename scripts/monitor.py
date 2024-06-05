#!/usr/bin/env python3.4

import start

from montr.cli import ResourceMonitorConsl


if __name__=='__main__':
    ResourceMonitorConsl( hostmode=True ).evaluate()
