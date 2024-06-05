#!/usr/bin/env python3.4

from abc import abstractmethod
from collections import OrderedDict, namedtuple
from enum import Enum
from itertools import repeat, chain
import sys

from translt.fieldnams import FieldnamesCollection


class ObjectNotFound(Exception):
    pass


class RecordFieldError(RuntimeError):
    pass


class ScalarTranslator:
    __slots__ = ()

    @abstractmethod
    def create_object(self, cntxt, value):
        ...

    @classmethod
    def get_value(cls, obj, path=None):
        if path:
            raise RecordFieldError( 'Path {} not found'.format( path ) )

        return obj



class IntegerTranslator(ScalarTranslator):
    __slots__ = ()

    @classmethod
    def create_object(cls, cntxt, value):
        return int( value )


class FloatTranslator(ScalarTranslator):
    __slots__ = ()

    @classmethod
    def create_object(cls, cntxt, value):
        return float( value )


class StringTranslator(ScalarTranslator):
    __slots__ = ()

    @classmethod
    def create_object(cls, cntxt, value):
        assert isinstance( value, str )
        return value


class BooleanTranslator(ScalarTranslator):
    __slots__ = ()

    @classmethod
    def create_object(cls, cntxt, value):
        if value==True or value==False:
            return value
        elif value in ('Y', 'y', 'TRUE', 'True', 'true', 'YES', 'Yes', 'yes', 'ON', 'On', 'on'):
            return True
        elif value in ('N', 'n', 'FALSE', 'False', 'false', 'NO', 'No', 'no', 'OFF', 'Off', 'off'):
            return False
        else:
            raise ValueError( value )



class OutputMode(Enum):
    no_output = 0x00
    write     = 0x01

    @property
    def is_writing(self):
        return self==OutputMode.write



class InputMode(Enum):
    no_input  = 0x00
    update    = 0x06
    set       = 0x0B
    construct = 0x05

    @property
    def is_creating(self):
        return ( self.value & 0x01 )!=0

    @property
    def is_modifying(self):
        return ( self.value & 0x02 )!=0

    @property
    def is_updating(self):
        return self==InputMode.update

    @property
    def is_setting(self):
        return self==InputMode.set

    @property
    def is_constructing(self):
        return self==InputMode.construct



class ElementEntry:
    __slots__ = 'element', 'inputmode', 'outputmode'

    def __init__(self, element):
        self.element    = element
        self.inputmode  = InputMode.no_input
        self.outputmode = OutputMode.no_output



class ContainerTranslator:
    def __init__(self):
        self.name_proc = None

    def prepare_input(self, cntxt):
        self._start_processing( cntxt )
        return False

    def process_object(self, cntxt, obj, iterable):
        raise NotImplementedError()

    def finish_input(self, cntxt):
        self._finish_processing( cntxt )

    def prepare_output(self, cntxt, obj):
        self._start_processing( cntxt )

    def finish_output(self, cntxt, obj):
        self._finish_processing( cntxt )

    # TODO: subfield -> element
    def input_subfields(self, cntxt):
        raise NotImplementedError()

    def creating_subfields(self, cntxt, obj):
        raise NotImplementedError()

    def updating_subfields(self, cntxt, obj):
        raise NotImplementedError()

    def prepare_subfield_input(self, cntxt, index, element, obj):
        return element.prepare_input( cntxt, self, index, obj )

    def process_subfield_input(self, cntxt, index, element, obj):
        return element.process_input( cntxt, self, index, obj )

    def finish_subfield_input(self, cntxt, index, element):
        element.finish_input( cntxt, self, index )

    def output_subfields(self, cntxt, obj):
        raise NotImplementedError()

    def prepare_subfield_output(self, cntxt, index, element, obj):
        return element.prepare_output( cntxt, self, index, obj )

    def finish_subfield_output(self, cntxt, index, element, obj):
        element.finish_output( cntxt, self, index, obj )

    def _start_processing(self, cntxt):
        if self.name_proc:
            cntxt.name_procs.append( self.name_proc )

    def _finish_processing(self, cntxt):
        if self.name_proc:
            cntxt.name_procs.pop()

    def _get_full_fieldname(self, cntxt, index, element, namegen, idxproc):
        if namegen.is_integrated( index ):
            return None

        base = namegen.for_index( index, cntxt, idxproc )

        if not cntxt.name_procs:
            return base
        else:
            parts = [base]
            for proc in cntxt.name_procs[ ::-1 ]:
                element.apply_name_proc( cntxt, self, parts, proc )

            return ''.join( parts )

    @abstractmethod
    def get_element(self, index):
        ...

    @abstractmethod
    def __contains__(self, index):
        ...

    def __getitem__(self, index):
        return self.get_element( index ).subfield

    def get_value_for_index(self, obj, index):
        element = self.get_element( index )

        if not element.has_value( None, self, index, obj ):
            raise ObjectNotFound()
        else:
            return element.get_value( None, self, index, obj )

    def get_value(self, obj, path=None):
        if obj is None or not path:
            return obj
        else:
            index, *rempath = path

            if not index in self:
                raise ObjectNotFound()

            return self[ index ].get_translator().get_value( self.get_value_for_index( obj, index ), rempath )

    @property
    @abstractmethod
    def is_finite(self):
        ...



#class NestedContainerTranslator(ContainerTranslator):
#    def __init__(self, nested):
#        self.nested = nested
#
#    def __contains__(self, index):
#        return index in self.nested
#
#    def get_element(self, index):
#        return self.nested.get_element( index )
#
#    def get_value_for_index(self, obj, index):
#        return self.nested.get_value_for_index( obj, index )
#
#    def prepare_input(self, cntxt):
#        return self.nested.prepare_input( cntxt )
#
#    def finish_input(self, cntxt):
#        self.nested.finish_input( cntxt )
#
#    def prepare_output(self, cntxt, obj):
#        return self.nested.prepare_output( cntxt, obj )
#
#    def finish_output(self, cntxt, obj):
#        self.nested.finish_output( cntxt, obj )
#
#    def prepare_subfield_input(self, cntxt, index, subfield, obj):
#        return self.nested.prepare_subfield_input( cntxt, index, subfield, obj )
#
#    def process_subfield_input(self, cntxt, index, subfield, obj):
#        return self.nested.process_subfield_input( cntxt, index, subfield, obj )
#
#    def finish_subfield_input(self, cntxt, index, subfield):
#        self.nested.finish_subfield_input( cntxt, index, subfield )
#
#    def prepare_subfield_output(self, cntxt, index, subfield, obj):
#        return self.nested.prepare_subfield_output( cntxt, index, subfield, obj )
#
#    def finish_subfield_output(self, cntxt, index, subfield, obj):
#        self.nested.finish_subfield_output( cntxt, index, subfield, obj )
#
#    def process_object(self, cntxt, obj, iterable):
#        return self.nested.process_object( cntxt, obj, iterable )
#
#    def creating_subfields(self, cntxt, obj):
#        yield from self.nested.creating_subfields( cntxt, obj )
#
#    def updating_subfields(self, cntxt, obj):
#        yield from self.nested.updating_subfields( cntxt, obj )
#
#    def output_subfields(self, cntxt, obj):
#        yield from self.nested.output_subfields( cntxt, obj )



class CollectionTranslator(ContainerTranslator):
    def __init__(self, fac):
        super().__init__()

        self.factory = fac

        self.default_element    = None
        self.default_inputmode  = InputMode.no_input
        self.default_outputmode = OutputMode.no_output

    def set_element(self, index, element):
        assert index is None and self.default_element is None
        self.default_element = element

    def set_inputmode(self, index, inpmod):
        assert index is None
        self.default_inputmode = inpmod

    def set_outputmode(self, index, outmod):
        assert index is None
        self.default_outputmode = outmod

    def _create_object(self, cntxt, obj, iterable):
        # TODO: compare types and directly use iterable? -> field format must not use generators
        return self.factory( iterable )

    def _update_object(self, cntxt, obj, values):
        for index, value in values:
            element = self.get_element( index )
            if element==self.default_element and self.default_inputmode.is_creating or index in self._creating:
                element.set_value( cntxt, self, index, obj, value)
        return obj

    @property
    def is_finite(self):
        return len( self )<sys.maxsize



# TODO: It's a mess.
class SequenceTranslator(CollectionTranslator):
    def __init__(self, fac=list):
        super().__init__( fac )

        self.max_count = None
        self.elements  = []
        self._creating = set()
        self._updating = set()
        self._writing  = set()

    def set_element(self, index, element):
        if index is None:
            super().set_element( index, element )
        elif index==len( self.elements ):
            self.elements.append( element )
        elif index>len( self.elements ):
            self.elements.extend( repeat( None, index-len( self.elements ) ) )
            self.elements.append( element )
        else:
            assert self.elements[ index ] is None
            self.elements[ index ] = element

    def set_inputmode(self, index, inpmod):
        if index is None:
            super().set_inputmode( index, inpmod )
        else:
            self._creating.discard( index )
            self._updating.discard( index )

            if inpmod.is_creating:
                self._creating.add( index )
            elif inpmod.is_updating:
                self._updating.add( index )

    def set_outputmode(self, index, outmod):
        if index is None:
            super().set_outputmode( index, outmod )
        elif outmod.is_writing:
            self._writing.add( index )
        else:
            self._writing.discard( index )

    def process_object(self, cntxt, obj, iterable):
        if obj is None:
            return self._create_object( cntxt, obj, iterable )
        else:
            return self._update_object( cntxt, obj, enumerate( iterable ) )

    def creating_subfields(self, cntxt, obj):
        if not self._creating and not self.default_inputmode.is_creating:
            yield from ()
        else:
            for index, element in self._enumerate( len( self ) ):
                self._check_element( element )
                if element==self.default_element and self.default_inputmode.is_creating or index in self._creating:
                    yield index, None, element

    def updating_subfields(self, cntxt, obj):
        if not self._updating and not self.default_inputmode.is_updating:
            yield from ()
        elif obj is not None:
            for index, element in self._enumerate( len( obj ) ):
                if element==self.default_element and self.default_inputmode.is_updating or index in self._updating:
                    yield index, None, element, element.get_value( cntxt, self, index, obj )

    # TODO: rename writing_elements
    def output_subfields(self, cntxt, obj):
        if not self._writing and not self.default_outputmode.is_writing:
            yield from ()
        elif obj is not None:
            for index, element in self._enumerate( len( obj ) ):
                if element==self.default_element and self.default_outputmode.is_writing or index in self._writing:
                    yield index, None, element, element.get_value( cntxt, self, index, obj )

    def _check_element(self, element):
        if not element:
            raise NotImplementedError( 'Lists with gaps are not supported.' )

    # TODO: Generated elements don't need to be contained in the object.
    def _enumerate(self, avail_count):
        if avail_count is not None:
            cnt = min( avail_count, len( self ) )
        elif self.is_finite:
            cnt = len( self )
        else:
            cnt = None

        if cnt is None:
            yield from ()
        elif self.default_element is None:
            yield from enumerate( self.elements[ :cnt ] )
        elif not self.elements:
            yield from enumerate( repeat( self.default_element, cnt ) )
        else:
            elemiter = iter( self.elements )
            for i in range( cnt ):
                element = next( elemiter, None )
                yield i, element if element is not None else self.default_element

    def __contains__(self, index):
        return index<len( self )

    def __len__(self):
        if self.max_count is not None:
            return self.max_count
        elif self.default_element is None:
            return len( self.elements )
        else:
            return sys.maxsize

    def get_element(self, index):
        element = None

        if index is not None and index<len( self.elements ):
            element = self.elements[ index ]

        if element is None:
            if self.default_element is None:
                raise KeyError( index )
            else:
                element = self.default_element

        return element



class NamedSequenceTranslator(SequenceTranslator):
    def __init__(self, fac=list):
        super().__init__( fac )

        self.namegenerator = FieldnamesCollection( '{}', int )
        self.index_offset   = 0

    def process_object(self, cntxt, obj, values):
        if obj is None:
            return self._create_object( cntxt, obj, self._transformed_values( values ) )
        else:
            return self._update_object( cntxt, obj, values )

    def _transformed_values(self, values):
        for _, value in values:
            yield value

    def creating_subfields(self, cntxt, obj, avail_names=None):
        for index, _, element in super().creating_subfields( cntxt, obj ):
            fullname = self._get_full_fieldname( cntxt, index, element )
            yield index, fullname, element

    def updating_subfields(self, cntxt, obj, avail_names=None):
        for index, _, element, value in super().updating_subfields( cntxt, obj ):
            fullname = self._get_full_fieldname( cntxt, index, element )
            yield index, fullname, element, value

    def output_subfields(self, cntxt, obj):
        for index, _, element, value in super().output_subfields( cntxt, obj ):
            fullname = self._get_full_fieldname( cntxt, index, element )
            yield index, fullname, element, value

    def _index_processor(self, index):
        return index+self.index_offset

    def _get_full_fieldname(self, cntxt, index, element):
        idxproc = None if self.index_offset==0 else self._index_processor
        return super()._get_full_fieldname( cntxt, index, element, self.namegenerator, idxproc )



class DictionaryTranslator(CollectionTranslator):
    def __init__(self, fac=dict, index_type=str):
        super().__init__( fac )

        self.elements  = OrderedDict()
        self._creating = set()
        self._updating = set()
        self._writing  = set()

        self.namegenerator = FieldnamesCollection( '{}', index_type )
        self.input_keyprocessor  = None
        self.output_keyprocessor = None

    def set_element(self, index, element):
        if index is None:
            super().set_element( index, element )
        else:
            assert index not in self.elements
            self.elements[ index ] = ElementEntry( element )

    def set_inputmode(self, index, inpmod):
        if index is None:
            super().set_inputmode( index, inpmod )
        else:
            entry = self.elements[ index ]

            if inpmod==entry.inputmode:
                return

            if entry.inputmode.is_creating:
                self._creating.discard( index )
            elif entry.inputmode.is_updating:
                self._updating.discard( index )

            entry.inputmode = inpmod

            if inpmod.is_creating:
                self._creating.add( index )
            elif inpmod.is_updating:
                self._updating.add( index )

    def set_outputmode(self, index, outmod):
        if index is None:
            super().set_outputmode( index, outmod )
        else:
            entry = self.elements[ index ]

            if outmod==entry.outputmode:
                return

            if entry.outputmode.is_writing:
                self._writing.discard( index )
            else:
                self._writing.add( index )

            entry.outputmode = outmod

    def prepare_input(self, cntxt):
        super().prepare_input( cntxt )
        return self.default_element is not None # available indices have to be provided by the field format.

    def process_object(self, cntxt, obj, values):
        if obj is None:
            return self._create_object( cntxt, obj, values )
        else:
            return self._update_object( cntxt, obj, values )

    def _keys(self, avail_indices=None):
        if self.default_element is None:
            keys = self.elements.keys()
        elif avail_indices is None:
            raise RecordFieldError( 'Cannot determine fields for unbound collection {}'.format( self ) )
        else:
            keys = avail_indices

        return keys if self.input_keyprocessor is None else self.input_keyprocessor( keys )

    def _items(self, cntxt, avail_indices=None):
        for index in self._keys( avail_indices ):
            element = self.get_element( index )
            name    = self._get_full_fieldname( cntxt, index, element, self.namegenerator, None )

            yield index, name, element

    def creating_subfields(self, cntxt, obj, avail_names=None):
        if not self._creating and not self.default_inputmode.is_creating:
            yield from ()
        else:
            avail_indices = self._guess_available_indices( cntxt, avail_names )

            for index, name, element in self._items( cntxt, avail_indices ):
                if element==self.default_element and self.default_inputmode.is_creating or index in self._creating:
                    yield index, name, element

    def updating_subfields(self, cntxt, obj, avail_names=None):
        if not self._updating and not self.default_inputmode.is_updating:
            yield from ()
        else:
            for index, name, element in self._items( cntxt, self._keys_for_object( obj ) ):
                if ( element==self.default_element and self.default_inputmode.is_updating or index in self._updating ) and \
                        element.has_value( cntxt, self, index, obj ):
                    yield index, name, element, element.get_value( cntxt, self, index, obj )

    def output_subfields(self, cntxt, obj):
        if not self._writing and not self.default_outputmode.is_writing:
            yield from ()
        else:
            for index, name, element in self._items( cntxt, self._keys_for_object( obj ) ):
                if ( element==self.default_element and self.default_outputmode.is_writing or index in self._writing ) and \
                        element.has_value( cntxt, self, index, obj ):
                    yield index, name, element, element.get_value( cntxt, self, index, obj )

    def _keys_for_object(self, obj):
        if obj is None:
            return ()
        elif self.output_keyprocessor is None:
            return self._object_keys( obj )
        else:
            return self.output_keyprocessor( self._object_keys( obj )   )

    def _object_keys(self, obj):
        return obj.keys()

    def __contains__(self, index):
        return True if self.default_element else index in self.elements

    def __len__(self):
        if self.default_element is None:
            return len( self.elements )
        else:
            return sys.maxsize

    def get_element(self, index):
        if index in self.elements:
            return self.elements[ index ].element
        elif self.default_element is None:
            raise KeyError( index )
        else:
            return self.default_element

    @classmethod
    def _guess_index_for_fieldname(cls, cntxt, element, namegen, name):
        if element:
            for proc in cntxt.name_procs:
                name = element.revoke_name_processor( cntxt, self, name, proc )

        guess = namegen.guess_index( name )

        return guess if guess is not None else name

    def _guess_available_indices(self, cntxt, avail_names):
        if avail_names is None:
            return None
        else:
            avail_indices = []

            for name in avail_names:
                index = self._guess_index_for_fieldname( cntxt, self.default_element, self.namegenerator, name )
                avail_indices.append( index )

            return avail_indices



# TODO: There are one or two problems with infinite generic items: The format has to provide all available
#       keys in a deterministic order. However, at least YAML, maybe JSON too, is not yet able to do that.
#       For flat, a patterned dictionary format has to be provided. This might lead to the introduction of
#       an additional metatype or, better, dictionary has to get a default pattern for guessing available
#       indices on basis of current name processors and the configured field separator.
class GenericNamedtupleTranslator(DictionaryTranslator):
    def __init__(self):
        super().__init__( None )

        self.typename = '_'

    def process_object(self, cntxt, obj, iterable):
        if obj is not None:
            RecordFieldError( 'Named tuple cannot be updated.' )

        if self.default_element is not None:
            raise NotImplementedError( 'Generic named tuple with infinite items are not supported yet.' )

        values = OrderedDict( iterable )
        return namedtuple( self.typename, values.keys() )( **values )

    def _object_keys(self, obj):
        return obj._fields



class CompositeTranslator(ContainerTranslator):
    def __init__(self, fac):
        super().__init__()

        self.factory         = fac
        self.namegenerator   = FieldnamesCollection( '{}', str )
        self.elements        = {}
        self._constructing   = []
        self._kwconstructing = OrderedDict()
        self._setting        = []
        self._updating       = []
        self._writing        = []

    def set_element(self, index, element):
        assert index not in self.elements
        self.elements[ index ] = ElementEntry( element )

    def set_constructorkeyword(self, index, keyword):
        self.set_inputmode( index, InputMode.construct )
        self._kwconstructing[ index ] = keyword

    def set_inputmode(self, index, inpmod):
        entry = self.elements[ index ]

        if inpmod==entry.inputmode:
            return

        if entry.inputmode.is_constructing:
            if self._kwconstructing.pop( index, None ) is None:
                self._constructing.remove( index )
        elif entry.inputmode.is_setting:
            self._setting.remove( index )
        elif entry.inputmode.is_updating:
            self._updating.remove( index )

        entry.inputmode = inpmod

        if inpmod.is_constructing:
            self._constructing.append( index )
        elif inpmod.is_setting:
            self._setting.append( index )
        elif inpmod.is_updating:
            self._updating.append( index )

    def set_outputmode(self, index, outmod):
        entry = self.elements[ index ]

        if outmod==entry.outputmode:
            return

        if entry.outputmode.is_writing:
            self._writing.remove( index )
        else:
            self._writing.append( index )

        entry.outputmode = outmod


    def process_object(self, cntxt, obj, iterable):
        const_args   = []
        const_kwargs = {}

        # TODO: Try to move the control flow to the translators and to use formats as strategies.
        if not self._updating or self._setting or ( obj is None and ( self._constructing or self._kwconstructing ) ):
            values = dict( iterable )
        else:
            values = {}

        if obj is None:
            for index in self._constructing:
                if index not in self._kwconstructing:
                    const_args.append( values.get( index, None ) )

            for index, keyword in self._kwconstructing.items():
                if index in values:
                    const_kwargs[ keyword ] = values[ index ]

            obj = self._do_create_object( const_args, const_kwargs )

        for index in self._setting:
            if index in values:
                element = self.get_element( index )
                element.set_value( cntxt, self, index, obj, values[ index ] )

        return obj

    def _do_create_object(self, args, kwargs):
        return self.factory( *args, **kwargs )

    # TODO: subfield -> elements?
    def creating_subfields(self, cntxt, obj, avail_names=None):
        idxs = chain( self._constructing, self._kwconstructing.keys(), self._setting) if obj is None else self._setting

        for index in idxs:
            element   = self.get_element( index )
            fieldname = self._get_full_fieldname( cntxt, index, element )
            yield index, fieldname, element

    def updating_subfields(self, cntxt, obj, avail_names=None):
        yield from self._subfield_values( cntxt, obj, self._updating )

    def output_subfields(self, cntxt, obj):
        yield from self._subfield_values( cntxt, obj, self._writing )

    def _subfield_values(self, cntxt, obj, idxs):
        for index in idxs:
            element = self.get_element( index )

            # TODO: If values are not returned by generators (and the control flow is part of the translators)
            #       elements could decide (by imperative method invocation) if a subfield shall be processed.
            #       element.has_value() would be obsolete.
            if element.has_value( cntxt, self, index, obj ):
                fieldname = self._get_full_fieldname( cntxt, index, element )
                value     = element.get_value( cntxt, self, index, obj )

                yield index, fieldname, element, value

    def get_element(self, index):
        return self.elements[ index ].element

    def __contains__(self, index):
        return index in self.elements

    def _get_full_fieldname(self, cntxt, index, element):
        return super()._get_full_fieldname( cntxt, index, element, self.namegenerator, None )

    @property
    def is_finite(self):
        return True
