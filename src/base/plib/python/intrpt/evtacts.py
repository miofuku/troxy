#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
from intrpt.format import ExprFormatter


class EventAction(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def __call__(self, cntxt, expr):
        ...

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    def _get_value_reprs(self):
        return []

    def format(self, exprfmt):
        exprfmt.item( super().__str__() )

    def __str__(self):
        return ExprFormatter().format( self ).create_str()


class Default(EventAction):
    __slots__ = 'destination', 'value'

    def __init__(self, dest, value):
        self.destination = dest
        self.value = value

    def __call__(self, cntxt, expr):
        store = cntxt.args_store
        if self.destination not in store:
            store[ self.destination ] = self.value

    def format(self, exprfmt):
        return exprfmt.item( '@default({}={})'.format( self.destination, self.value ) )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [ repr( self.destination ), repr( self.value ) ]
