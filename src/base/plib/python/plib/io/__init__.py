#!/usr/bin/env python3.4

import io


class IOWrapper:
    __slots__ = '_stream'

    def __init__(self, stream):
        self._stream = stream

    def seek(self, pos, whence=0):
        return self._stream.seek( pos, whence )

    def tell(self):
        return self._stream.tell()

    def truncate(self, pos=None):
        return self._stream.truncate( pos )

    def flush(self):
        self._stream.flush()

    def close(self):
        self._stream.close()

    def __del__(self):
        del self._stream

    def seekable(self):
        return self._stream.seekable()

    def readable(self):
        return self._stream.readable()

    def writable(self):
        return self._stream.writeable()

    @property
    def closed(self):
        return self._stream.closed

    def __enter__(self):
        return self._stream.__enter__()

    def __exit__(self, *args):
        self._stream.__exit__()

    def fileno(self):
        return self._stream.fileno()

    def isatty(self):
        return self._stream.isatty()

    def readline(self, limit=-1):
        return self._stream.readline( limit )

    def __iter__(self):
        return iter( self._stream.__iter__() )

    def next(self):
        return next( self._stream )

    def readlines(self, hint=None):
        return self._stream.readlines( hint )

    def writelines(self, lines):
        self._stream.writelines( lines )

io.IOBase.register( IOWrapper )  #@UndefinedVariable


class BufferedIOWrapper(IOWrapper):
    __slots__ = ()

    def read(self, n=None):
        return self._stream.read( n )

    def read1(self, n=None):
        return self._stream.read1( n )

    def readinto(self, b):
        return self._stream.readinto( b )

    def write(self, b):
        return self._stream.write( b )

    def detach(self):
        self._stream.detach()

io.BufferedIOBase.register( BufferedIOWrapper )  #@UndefinedVariable
