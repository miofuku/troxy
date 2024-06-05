#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
from collections.abc import Iterator


class InputMapping(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def read_from(self, src):
        ...



class OutputMapping(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def write_to(self, snk, obj):
        ...



class Mapping(InputMapping, OutputMapping):
    __slots__ = ()



class DirectMapping(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def read_to(self, obj, src):
        ...



class Format(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def read(self, src, mapping):
        ...

    @abstractmethod
    def write(self, obj, snk, mapping):
        ...



class ObjectReader(Iterator):
    __slots__ = ()

    @abstractmethod
    def read_object(self, obj=None):
        ...

    def __next__(self):
        return self.read_object()



class ObjectWriter(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def write_object(self, obj):
        ...

    def append(self, obj):
        self.write_object( obj )

    def extend(self, objs):
        for obj in obj:
            self.write_object( obj )



class ListToSingleObject(InputMapping):
    def __init__(self, mapping):
        self.mapping = mapping

    def read_from(self, src):
        obj_list = self.mapping.read_from( src )

        assert not obj_list or len( obj_list )==1

        return obj_list[ 0 ] if obj_list else None
