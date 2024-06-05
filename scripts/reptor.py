#!/usr/bin/env python3.4

import start

from pathlib import Path

from reptor.consl import ReptorConsl
from reptor.execenv import ReptorExecutionEnvironment
from reptor.system import Reptor


def execution_environment():
    this   = Path( __file__ ).resolve().absolute()
    prjdir = this.parents[ 1 ]

    return ReptorExecutionEnvironment( prjdir, this )


def main():
    execenv = execution_environment()
    system  = Reptor()

    execenv.set_system( system )

    ReptorConsl( execenv ).evaluate()
    system.hibernate()


if __name__=='__main__':
    main()
