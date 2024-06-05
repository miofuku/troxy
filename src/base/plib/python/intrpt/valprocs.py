#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
import operator

from plib.utils.builders import FluidMixin
from plib.utils import callable_singleton


class ValueProcessor(metaclass=ABCMeta):
    def __init__(self, inc_error_level=False):
        self.increases_error_level = inc_error_level

    # Gets converted value, the current context and the variable,
    # returns processed/transformed value or set the error state of the context.
    # If multiple values shall be handled, value can be also a sequence!
    @abstractmethod
    def __call__(self, cntxt, expr, value, error_level):
        ...

    def _on_error(self, cntxt, expr, err, level):
        cntxt.on_value_error( expr, suberrs=err, level=level )

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    def _get_value_reprs(self):
        return []

    Empty = None

ValueProcessor.Empty = \
        callable_singleton( 'Empty', ValueProcessor, lambda self, cntxt, expr, value, error_level: value )


class ValueProcessorChain(ValueProcessor, FluidMixin):
    __slots__ = '_procs'

    def __init__(self, procs=None):
        super().__init__()
        self._procs = [] if procs is None else procs

    def add_processor(self, proc):
        self._procs.append( proc )
        return self

    def extend_processors(self, procs):
        self._procs.extend( procs )
        return self

    def insert_processor(self, index, proc):
        self._procs.insert( index, proc )
        return self

    def get_processors(self):
        return self._procs

    def remove_processor(self, proc):
        self._procs.remove( proc )
        return self

    def convert_lower(self):
        return self.add_processor( Converter.Lower )

    def convert_int(self):
        return self.add_processor( Converter.Int )

    def convert_float(self):
        return self.add_processor( Converter.Float )

    def __call__(self, cntxt, expr, value, error_level):
        v = value
        for proc in self._procs:
            try:
                v = proc( cntxt, expr, v, error_level )
            except ValueError as err:
                self._on_error( cntxt, expr, err, error_level )
                break

            if cntxt.has_error:
                break
            elif hasattr( proc, 'increases_error_level' ) and proc.increases_error_level:
                error_level += 1
        return v

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( proc ) for proc in self._procs]

    def __str__(self):
        return '<{}>'.format( '+'.join( [str( proc ) for proc in self._procs] ) )


class Converter(ValueProcessor):
    __slots__ = 'conv_func'

    def __init__(self, conv_func, inc_error_level=False):
        super().__init__( inc_error_level )
        self.conv_func = conv_func

    def __call__(self, cntxt, expr, value, error_level):
        return self.conv_func( value )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.conv_func )]

    Int   = None
    Float = None
    Lower = None

Converter.Int   = Converter( int, True )
Converter.Float = Converter( float, True )
Converter.Lower = Converter( str.lower )


class ValueChecker(ValueProcessor):
    __slots__ = 'negate', 'msg'

    def __init__(self, negate=False, inc_error_level=True, msg=None, msg_add=None):
        super().__init__( inc_error_level )
        self.negate = negate

        if msg is not None:
            self.msg = msg
        elif msg_add:
            self.msg = '{{}} must {}{} (given: \'{{}}\')'.format( 'not ' if negate else '', msg_add )
        else:
            self.msg = 'Wrong value for {}: \'{}\''

    def __call__(self, cntxt, expr, value, error_level):
        if self._check( cntxt, expr, value )==self.negate:
            self._on_error( cntxt, expr, ValueError( self.msg.format( str(expr), value ) ), error_level )
        return value

    @abstractmethod
    def _check(self, cntxt, expr, value):
        ...

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.negate )]


class RangeChecker(ValueChecker):
    __slots__ = '_lower_bound', '_upper_bound', 'min_value', 'max_value', 'incl_min', 'incl_max'

    def __init__(self, minval, maxval=None, inclmin=True, inclmax=True, negate=False, inc_error_level=True, msg=None):
        assert minval is not None or maxval is not None

        msgadd = [ 'be' ]
        if minval is None:
            self._lower_bound = self._no_bound
        else:
            if inclmin:
                msgadd.append( '>=' )
                self._lower_bound = operator.ge
            else:
                msgadd.append( '>' )
                self._lower_bound = operator.gt
            msgadd.append( str( minval ) )

        if maxval is None:
            self._upper_bound = self._no_bound
        else:
            if minval is not None:
                msgadd.append( 'and' )
            if inclmax:
                msgadd.append( '<=' )
                self._upper_bound = operator.le
            else:
                msgadd.append( '<' )
                self._upper_bound = operator.lt
            msgadd.append( str( maxval ) )

        super().__init__( negate, inc_error_level, msg, ' '.join( msgadd ) )

        self.min_value = minval
        self.max_value = maxval
        self.incl_min  = inclmin
        self.incl_max  = inclmax

    def _no_bound(self, a, b):
        return True

    def _check(self, cntxt, expr, value):
        return self._lower_bound( value, self.min_value ) and self._upper_bound( value, self.max_value )

    def _get_value_reprs(self):
        return super()._get_value_reprs() \
                + [repr( e ) for e in (self.min_value, self.max_value, self.incl_min, self.incl_max)]


class RegexChecker(ValueChecker):
    __slots__ = 're'

    def __init__(self, re, negate=False, inc_error_level=True, msg=None):
        super().__init__( negate, inc_error_level, msg, 'match ' + str( re ) )
        self.re = re

    def _check(self, cntxt, expr, value):
        return self.re.search( value ) is not None

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.re )]


class StartsWithChecker(ValueChecker):
    __slots__ = 'start'

    def __init__(self, start, negate=False, inc_error_level=True, msg=None):
        super().__init__( negate, inc_error_level, msg, 'start with ' + start )
        self.start = start

    def _check(self, cntxt, expr, value):
        return value.startswith( self.start )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.start )]


class IsInChecker(ValueChecker):
    __slots__ = 'list'

    def __init__(self, list_, negate=False, inc_error_level=True, msg=None):
        super().__init__( negate, inc_error_level, msg, 'be in ' + str( list_ ) )
        self.list = list_

    def _check(self, cntxt, expr, value):
        return value in self.list

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.list )]
