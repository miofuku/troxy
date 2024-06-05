#!/usr/bin/env python3.4

from collections.abc import Sized, Iterable, Container

from plib.utils import Value


# Only for very few referenced objects
class ReferenceCounter(Sized, Iterable, Container):
    class _RefCount:
        __slots__ = 'object', 'data', 'count'

        def __init__(self, obj, data, cnt):
            self.object = obj
            self.data   = data
            self.count  = cnt

    __slots__ = '_refs'

    def __init__(self):
        self._refs = []

    def get(self, obj, default=Value.Undefined):
        e = next( (e for e in self._refs if e.object==obj), default )

        if e==Value.Undefined:
            raise KeyError( obj )
        else:
            return e

    def add(self, obj):
        e = self.get( obj, None )

        if e is None:
            e = self._RefCount( obj, None, 0 )
            self._refs.append( e )

        e.count += 1
        return e

    def remove(self, obj):
        e = self.get( obj )
        e.count -= 1

        if e.count==0:
            self._refs.remove( e )

        return e

    def __len__(self):
        return len( self._refs )

    def __getitem__(self, obj):
        return self.get( obj )

    def __contains__(self, obj):
        return self.get( obj, None ) is not None

    def __iter__(self):
        yield from [e for e in self._refs]
