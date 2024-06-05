#!/usr/bin/env python3.4

import start

from plib.shell.java import JPS, JStack

if __name__=='__main__':
     for jproc in JPS.javaprocesses().values():
        for jthread in JStack.javathreads( jproc.pid ).values():
            print( '{} (pid={}): {:40} (tid={:>5})'.format( jproc.name, jproc.pid, jthread.name, jthread.pid ) )
