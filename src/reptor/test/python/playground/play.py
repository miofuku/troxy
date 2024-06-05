#!/usr/bin/env python3.4

from _functools import reduce
import time


print( 'Start' )
starttime = time.monotonic()


def seconds_to_str(secs):
    tp = reduce( lambda ll, b: divmod( ll[ 0 ], b ) + ll[ 1: ], [(secs*1000000,), 1000000, 60] )
    return '{:02.0f}:{:02.0f}.{:06.0f}'.format( *list( tp ) )

print( 'barcardi finished. ' + seconds_to_str( time.monotonic()-starttime ) )
