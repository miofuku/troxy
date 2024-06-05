#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta

from plib.utils.builders import FluidMixin


#-------------------------------------------------------------------------------
# Format instructions
#----
class ExprFormatInstruction(metaclass=ABCMeta):
    @abstractmethod
    def apply(self, exprfmt):
        ...


class FormatCardinality(ExprFormatInstruction):
    __slots__ = 'min_count', 'max_count'

    Optional    = None
    AnyNumber   = None
    AtLeastOnce = None

    def __init__(self, mincnt, maxcnt):
        self.min_count = mincnt
        self.max_count = maxcnt

    def apply(self, exprfmt):
        exprfmt.cardinality( self.min_count, self.max_count )

FormatCardinality.Optional    = FormatCardinality( 0, 1 )
FormatCardinality.AnyNumber   = FormatCardinality( 0, None )
FormatCardinality.AtLeastOnce = FormatCardinality( 1, None )


class FormatHasEnvelope(ExprFormatInstruction):
    __slots__ = ()

    @classmethod
    def apply(self, exprfmt):
        exprfmt.has_envelope()


class FormatForceEnvelope(ExprFormatInstruction):
    __slots__ = ()

    @classmethod
    def apply(self, exprfmt):
        exprfmt.force_envelope()


class FormatGap(ExprFormatInstruction):
    __slots__ = 'count'

    def __init__(self, count=1):
        self.count = count

    def apply(self, exprfmt):
        exprfmt.gap( self.count )


class FormatStrings(ExprFormatInstruction):
    __slots__ = 'values'

    def __init__(self, values):
        self.values = values

    def apply(self, exprfmt):
        exprfmt.string( *self.values )


class FormatPrefixedBase(FluidMixin, ExprFormatInstruction):
    __slots__ = '_prefixes'

    def __init__(self):
        super().__init__()
        self._prefixes = []

    def prefix(self, prefix):
        self._prefixes.append( prefix )
        return self

    def cardinality(self, mincnt, maxcnt):
        return self.prefix( FormatCardinality( mincnt, maxcnt ) )

    def optional(self):
        return self.prefix( FormatCardinality.Optional )

    def any_number(self):
        return self.prefix( FormatCardinality.AnyNumber )

    def at_least_once(self):
        return self.prefix( FormatCardinality.AtLeastOnce )

    def has_envelope(self):
        return self.prefix( FormatHasEnvelope )

    def force_envelope(self):
        return self.prefix( FormatForceEnvelope )

    def apply(self, exprfmt):
        for pref in self._prefixes:
            pref.apply( exprfmt )

        self._apply_element( exprfmt )

    def _apply_element(self, exprfmt):
        raise NotImplementedError()


class FormatItem(FormatPrefixedBase):
    __slots__ = 'strings', 'usegap'

    def __init__(self, strings, usegap=True):
        super().__init__()
        self.strings = strings
        self.usegap  = usegap

    def _apply_element(self, exprfmt):
        exprfmt.item( *self.strings, usegap=self.usegap )


class FormatVariable(FormatPrefixedBase):
    __slots__ = 'name'

    def __init__(self, name):
        super().__init__()
        self.name = name

    def _apply_element(self, exprfmt):
        exprfmt.variable( self.name )


class FormatConstant(FormatPrefixedBase):
    __slots__ = 'value'

    def __init__(self, value):
        super().__init__()
        self.value = value

    def _apply_element(self, exprfmt):
        exprfmt.constant( self.value )


class FormatGroupBase(FormatPrefixedBase):
    __slots__ = '_nitems', '_elems', '_is_strong'

    def __init__(self, is_strong, nitems, elems):
        super().__init__()
        self._is_strong = is_strong
        self._nitems    = nitems
        self._elems     = [] if elems is None else elems

    @property
    def _requires_group(self):
        return self._nitems>1

    def add_element(self, elem):
        self._elems.append( elem )
        return self

    def add_item(self, item):
        self._nitems += 1
        return self.add_element( item )

    def init_item(self, item):
        self.add_item( item )
        return item.caller( self )

    def string(self, *values):
        return self.add_element( FormatStrings( values ) )

    def gap(self, count=1):
        return self.add_element( FormatGap( count ) )

    def start_item(self, *strings, usegap=True):
        return self.init_item( FormatItem( strings, usegap ) )

    def item(self, *strings, usegap=True):
        return self.add_item( FormatItem( strings, usegap ) )

    def start_variable(self, name):
        return self.init_item( FormatVariable( name ) )

    def variable(self, name):
        return self.add_item( FormatVariable( name ) )

    def start_constant(self, value):
        return self.init_item( FormatConstant( value ) )

    def constant(self, value):
        return self.add_item( FormatConstant( value ) )

    def start_complex_item(self):
        return self.init_item( FormatComplexItem() )

    def start_list_item(self, is_strong=True):
        return self.init_item( FormatList( is_strong ) )

    def start_sequence(self, is_strong=True):
        return self.init_item( FormatSequence( is_strong ) )

    def start_choice(self, is_strong=False):
        return self.init_item( FormatChoice( is_strong ) )

    def start_group(self, separator, is_strong):
        return self.init_item( FormatGroup( separator, is_strong ) )

    def apply(self, exprfmt):
        if self._requires_group:
            exprfmt = self._start_group( exprfmt )

        for s in self._elems:
            s.apply( exprfmt )

        if self._requires_group:
            exprfmt.end()

    def _start_group(self):
        raise NotImplementedError()


class FormatComplexItem(FormatGroupBase):
    __slots__ = ()

    def __init__(self, nitems=0, elems=None):
        super().__init__( True, nitems, elems )

    def _start_group(self, exprfmt):
        return exprfmt.start_item()

    @property
    def _requires_group(self):
        return True


class FormatList(FormatGroupBase):
    __slots__ = ()

    def __init__(self, is_strong=True, nitems=0, elems=None):
        super().__init__( is_strong, nitems, elems )

    def _start_group(self, exprfmt):
        return exprfmt.start_list( self._is_strong )


class FormatSequence(FormatGroupBase):
    __slots__ = ()

    def __init__(self, is_strong=True, nitems=0, elems=None):
        super().__init__( is_strong, nitems, elems )

    def _start_group(self, exprfmt):
        return exprfmt.start_sequence( self._is_strong )


class FormatChoice(FormatGroupBase):
    __slots__ = ()

    def __init__(self, is_strong=False, nitems=0, elems=None):
        super().__init__( is_strong, nitems, elems )

    def _start_group(self, exprfmt):
        return exprfmt.start_choice( self._is_strong )


class FormatGroup(FormatGroupBase):
    __slots__ = '_separator'

    def __init__(self, separator, is_strong, nitems=0, elems=None):
        super().__init__( is_strong, nitems, elems )
        self._separator = separator

    def _start_group(self, exprfmt):
        return exprfmt.start_group( self._separator, self._is_strong )


#-------------------------------------------------------------------------------
# Formatter
#----

class ExprFormatter:
    __slots__ = '_elements', '_group_stack', '_mincnt', '_maxcnt', '_has_envelope', '_force_envelope'

    class _GroupInfo:
        __slots__ = 'count', 'mincnt', 'maxcnt', 'needs_envelope', 'separator', 'is_strong'

        def __init__(self, mincnt, maxcnt, needs_envelope, separator, is_strong):
            self.count  = 0
            self.mincnt = mincnt
            self.maxcnt = maxcnt
            self.separator = separator
            self.is_strong = is_strong
            self.needs_envelope = needs_envelope

    def __init__(self):
        self._elements    = []
        self._group_stack = []

        self._mincnt = 1
        self._maxcnt = 1
        self._has_envelope   = False
        self._force_envelope = False

    def create_str(self):
        return ''.join( self._elements )

    def get_elements(self):
        return self._elements

    def apply(self, fmtins):
        fmtins.apply( self )
        return self

    def format(self, expr):
        expr.format( self )
        return self

    def cardinality(self, mincnt, maxcnt):
        assert mincnt>=0 and ( maxcnt is None or maxcnt>0 and maxcnt>=mincnt )
        self._mincnt = mincnt
        self._maxcnt = maxcnt
        return self

    def optional(self):
        return self.cardinality( 0, 1 )

    def any_number(self):
        return self.cardinality( 0, None )

    def at_least_once(self):
        return self.cardinality( 1, None )

    def get_min_count(self):
        return self._mincnt

    def get_max_count(self):
        return self._maxcnt

    def has_envelope(self, value=True):
        self._has_envelope = value
        return self

    def get_has_envelope(self):
        return self._has_envelope

    def force_envelope(self, value=True):
        self._force_envelope = value
        return self

    def get_force_envelope(self):
        return self._force_envelope

    def string(self, *strs):
        self._elements.extend( strs )
        return self

    def gap(self, count=1):
        self._elements.append( ' '*count )
        return self

    def item(self, *strs, usegap=True):
        self._start_item( usegap )
        self._elements.extend( strs )
        self._end_item()
        return self

    def variable(self, name):
        return self.item( '<', name, '>', usegap=False )

    def constant(self, value):
        return self.item( value, usegap=False )

    def start_group(self, separator, is_strong, usegap=True):
        self._force_envelope = self._force_envelope or not is_strong and self._group_stack and self._curgrp.is_strong

        self._start_item( usegap )

        gi = self._GroupInfo( self._mincnt, self._maxcnt, self._needs_envelope, separator, is_strong)
        self._group_stack.append( gi )
        self._renew_prefixes()

        return self

    def start_item(self, usegap=True):
        return self.start_group( None, True, usegap )

    def start_list(self, is_strong=True, usegap=True):
        return self.start_group( ' ', is_strong, usegap)

    def start_sequence(self, is_strong=True, usegap=False):
        return self.start_group( ' ', is_strong, usegap)

    def start_choice(self, is_strong=False, usegap=False):
        return self.start_group( ' | ', is_strong, usegap )

    def end(self):
        gi = self._group_stack.pop()
        self._mincnt = gi.mincnt
        self._maxcnt = gi.maxcnt
        self._force_envelope = gi.needs_envelope

        self._end_item()

        return self

    @property
    def _needs_envelope(self):
        return not self._has_envelope and self._force_envelope

    def _renew_prefixes(self):
        self._mincnt = 1
        self._maxcnt = 1
        self._has_envelope   = False
        self._force_envelope = False

    @property
    def _curgrp(self):
        return self._group_stack[ -1 ]

    def _start_item(self, usegap=False):
        if self._group_stack:
            if self._curgrp.count:
                self._add_separator( usegap )
            self._curgrp.count += 1

        self._add_cardinality_prefix()

        if self._needs_envelope:
            self._add_default_envelope_start()

    def _end_item(self):
        if self._needs_envelope:
            self._add_default_envelope_end()
            self._has_envelope = True

        self._add_cardinality_suffix()

        self._renew_prefixes()

    def _add_separator(self, usegap):
        if usegap:
            self.gap()
        elif self._curgrp.separator:
            self._elements.append( self._curgrp.separator )

    def _add_default_envelope_start(self):
        self._elements.append( '(' )

    def _add_default_envelope_end(self):
        self._elements.append( ')' )

    def _add_cardinality_prefix(self):
        if self._mincnt==0 and self._maxcnt==1:
            self._elements.append( '[' )
            self._has_envelope = True
        elif self._mincnt!=1 or self._maxcnt!=1:
            self._force_envelope = True

    def _add_cardinality_suffix(self):
        if self._mincnt==0 and self._maxcnt==1:
            self._elements.append( ']' )
        elif self._mincnt!=1 or self._maxcnt!=1:
            if not self._has_envelope:
                self._add_default_envelope_end()

            if self._mincnt==0 and self._maxcnt is None:
                self._elements.append( '*' )
            elif self._mincnt==1 and self._maxcnt is None:
                self._elements.append( '+' )
            else:
                self._add_cardinality_braces()

    def _add_cardinality_braces(self):
        if self._mincnt==self._maxcnt:
            self._elements.append( '{{{}}}'.format( self._mincnt ) )
        else:
            self._elements.append( '{{{},{}}}'.format( self._mincnt, self._maxcnt or '*' ) )
