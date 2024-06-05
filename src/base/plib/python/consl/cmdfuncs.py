#!/usr/bin/env python3

from abc import abstractmethod, ABCMeta


class NameMixin:
    def __init__(self, name):
        self._name    = name
        self._aliases = []

    def get_name(self):
        return self._name

    def alias(self, *alias):
        return self.extend_aliases( alias )

    def extend_aliases(self, aliases):
        self._aliases.extend( aliases )
        return self

    def get_aliases(self):
        return self._aliases


class NestedNameMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_name(self):
        ...

    def get_name(self):
        return self._nested_name.get_name()

    def alias(self, *alias):
        self._nested_name.alias( *alias )
        return self

    def extend_aliases(self, aliases):
        self._nested_name.extend_aliases( aliases )
        return self

    def get_aliases(self):
        return self._nested_name.get_aliases()
