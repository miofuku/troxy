#!/usr/bin/env python3.4

# Initialisation
import importlib.machinery
import os
import subprocess
import sys
from pathlib import Path


prjdir = Path( __file__ ).resolve().absolute().parents[ 1 ]
libdir = prjdir / 'libs'

# Config
pypaths = ('build', 'libs'), ('config', 'netenvs'), ('src', 'reptor', 'start', 'python'), ('src', 'exprmt', 'exprmt', 'python'), \
          ('src', 'base', 'plib', 'python')

extlibs = () #'plib', 'exprmt'

# Initialise Python path
fullspypaths = [str( Path.cwd() )]

for pp in pypaths:
    fullspypaths.append( str( prjdir.joinpath( *pp ) ) )

# Initialise external projects
for ext in extlibs:
    inifil = str( libdir / ext / 'scripts' / 'start.py' )
    importlib.machinery.SourceFileLoader( ext+'_start', inifil ).load_module()

# Local modules have a higher priority, even higher than the calling script.
fullspypaths.extend( sys.path )
sys.path = fullspypaths

if __name__=='__main__':
    os.environ[ 'PYTHONPATH' ] = ':'.join( sys.path )

    if len( sys.argv )>1:
        subprocess.call( sys.argv[ 1: ] )
    else:
        print( os.environ[ 'PYTHONPATH' ] )
