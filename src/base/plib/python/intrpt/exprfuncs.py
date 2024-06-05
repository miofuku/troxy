#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod

from intrpt.evtacts import Default
from intrpt.valprocs import Converter
from plib.utils import Value
from intrpt.valacts import Const, Store


class DefaultValueMixin(metaclass=ABCMeta):
    def __init__(self):
        self.__evtact = Default( None, Value.Undefined )

    @property
    @abstractmethod
    def _default_value_joint(self):
        ...

    def get_has_default_value(self):
        return self.__evtact.value!=Value.Undefined and self.__evtact.destination is not None

    def clear_default_value(self):
        b = self.get_has_default_value()
        self.__evtact.value = Value.Undefined
        return self.__on_change( b )

    def default_value(self, value):
        b = self.get_has_default_value()
        self.__evtact.value = value
        return self.__on_change( b )

    def get_default_value(self):
        assert self.get_has_default_value()
        return self.__evtact.value

    def destination(self, dest):
        b = self.get_has_default_value()
        self.__evtact.destination = dest
        return self.__on_change( b )

    def get_destination(self):
        return self.__evtact.destination

    def __on_change(self, before):
        after = self.get_has_default_value()

        if before and not after:
            self._default_value_joint.remove_action( self.__evtact )
        elif not before and after:
            self._default_value_joint.add_action( self.__evtact )

        return self


class NestedDefaultValueMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_default_value(self):
        ...

    def get_has_default_value(self):
        return self._nested_default_value.get_has_default_value()

    def clear_default_value(self):
        self._nested_default_value.clear_default_value()
        return self

    def default_value(self, value):
        self._nested_default_value.default_value( value )
        return self

    def get_default_value(self):
        return self._nested_default_value.get_default_value()

    def destination(self, dest):
        self._nested_default_value.destination( dest )
        return self

    def get_destination(self):
        return self._nested_default_value.get_destination()


class StandardActionMixin(metaclass=ABCMeta):
    def __init__(self):
        self.__actcls = None
        self.__stdact = None
        self.__dest   = None

    @property
    @abstractmethod
    def _standard_action_collection(self):
        ...

    def standard_action(self, actcls):
        if actcls!=self.__actcls:
            self.__actcls = actcls
            self.__on_change()
        return self

    def get_standard_action(self):
        return self.__actcls

    def destination(self, dest):
        if dest!=self.__dest:
            self.__dest = dest
            self.__on_change()
        return self

    def get_destination(self):
        return self.__dest

    def __on_change(self):
        if self.__stdact is not None:
            self._standard_action_collection.remove_action( self.__stdact )
            self.__stdact = None

        if self.__dest is not None and self.__actcls is not None:
            self.__stdact = self.__actcls( self.__dest )
            self._standard_action_collection.add_action( self.__stdact )


class ConstValueMixin(metaclass=ABCMeta):
    def __init__(self):
        self.__constact = Const( Store( None, False ), Value.Undefined )

    @property
    @abstractmethod
    def _nested_value_actions(self):
        ...

    def get_has_value(self):
        return self.__constact.value!=Value.Undefined and self.__constact.subact.destination is not None

    def clear_value(self):
        b = self.get_has_value()
        self.__constact.value = Value.Undefined
        return self.__on_change( b )

    def value(self, value):
        b = self.get_has_value()
        self.__constact.value = value
        return self.__on_change( b )

    def get_value(self):
        assert self.get_has_value()

        return self.__constact.value

    def override_value(self, value):
        self.__constact.subact.override = value
        return self

    def get_override_value(self):
        return self.__constact.subact.override

    def destination(self, dest):
        b = self.get_has_value()
        self.__constact.subact.destination = dest
        return self.__on_change( b )

    def get_destination(self):
        return self.__constact.subact.destination

    def __on_change(self, before):
        after = self.get_has_value()

        if before and not after:
            self._nested_value_actions.remove_action( self.__constact )
        elif not before and after:
            self._nested_value_actions.add_action( self.__constact )

        return self


class NestedConstValueMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_const_value(self):
        ...

    def get_has_value(self):
        return self._nested_const_value.get_has_value()

    def clear_value(self):
        self._nested_const_value.clear_value()
        return self

    def value(self, value):
        self._nested_const_value.value( value )
        return self

    def get_value(self):
        return self._nested_const_value.get_value()

    def override_value(self, value):
        self._nested_const_value.override_value( value )
        return self

    def get_overrde_value(self):
        return self._nested_const_value.get_override_value()

    def destination(self, value):
        self._nested_const_value.destination( value )
        return self

    def get_destination(self):
        return self._nested_const_value.get_destination()


class NestedValueActionMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_value_actions(self):
        ...

    def configure_actions(self):
        return self._nested_value_actions.caller( self )

    def add_action(self, action):
        self._nested_value_actions.add_action( action )
        return self

    def extend_actions(self, actions):
        self._nested_value_actions.extend_action( actions )
        return self

    def get_actions(self):
        return self._nested_value_actions.get_actions()

    def store(self, dest, override=False):
        self._nested_value_actions.store( dest, override )
        return self

    def store_const(self, dest, value, override=False):
        self._nested_value_actions.store_const( dest, value, override )
        return self

    def call(self, callable_):
        self._nested_value_actions.call( callable_ )
        return self

    def paramcall(self, callable_):
        self._nested_value_actions.paramcall( callable_ )
        return self

    def contextcall(self, callable_):
        self._nested_value_actions.contextcall( callable_ )
        return self


class NestedValueProcessorMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_value_processors(self):
        ...

    def configure_processors(self):
        return self._nested_value_processors.caller( self )

    def add_processor(self, proc):
        self._nested_value_processors.add_processor( proc )
        return self

    def extend_processors(self, procs):
        self._nested_value_processors.extend_processors( procs )
        return self

    def insert_processor(self, index, proc):
        self._nested_value_processors.insert_processor( index, proc )
        return self

    def remove_processor(self, proc):
        self._nested_value_processors.remove_processor( proc )
        return self

    def get_processors(self):
        return self._nested_value_processors.get_processors()

    def convert_lower(self):
        self._nested_value_processors.convert_lower()
        return self

    def convert_int(self):
        self._nested_value_processors.convert_int()
        return self

    def convert_float(self):
        self._nested_value_processors.convert_float()
        return self


class CompareLowercaseMixin(metaclass=ABCMeta):
    def __init__(self, cmplower=False):
        self.__cmplower = cmplower

    @property
    @abstractmethod
    def _cmplower_processor_collection(self):
        ...

    @classmethod
    def _change_lowercase(cls, procs, value):
        if value:
            procs.insert_processor( 0, Converter.Lower )
        else:
            procs.remove_processor( Converter.Lower )

    def _lowercase_changed(self, value):
        self._change_lowercase( self._cmplower_processor_collection, value )

    def compare_lowercase(self, value=True):
        if value!=self.__cmplower:
            self.__cmplower = value

            self._lowercase_changed( value )

        return self

    def get_compare_lowercase(self):
        return self.__cmplower


class NestedCompareLowercaseMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_cmplower(self):
        ...

    def compare_lowercase(self, value):
        self._nested_complower.compare_lowercase( value )
        return self

    def get_compare_lowercase(self):
        return self._nested_cmplower.get_compare_lowercase()


class CardinalityMixin:
    def __init__(self):
        self.__mincnt = 1
        self.__maxcnt = 1

    def optional(self):
        return self.cardinality( 0, 1 )

    def any_number(self):
        return self.cardinality( 0, None )

    def at_least_once(self):
        return self.cardinality( 1, None )

    def cardinality(self, mincnt, maxcnt):
        assert mincnt>=0 and (maxcnt is None or maxcnt>0 and maxcnt>=mincnt)
        self.__mincnt = mincnt
        self.__maxcnt = maxcnt
        return self

    def get_min_count(self):
        return self.__mincnt

    def get_max_count(self):
        return self.__maxcnt


class DestinationMixin:
    def __init__(self, dest=None):
        self._dest = dest

    def destination(self, value):
        self._dest = value
        return self

    def get_destination(self):
        return self._dest


class NestedDestinationMixin(metaclass=ABCMeta):
    __slots__ = ()

    @property
    @abstractmethod
    def _nested_destination(self):
        ...

    def destination(self, value):
        self._nested_destination.destination( value )
        return self

    def get_destination(self):
        return self._nested_destination.get_destination()
