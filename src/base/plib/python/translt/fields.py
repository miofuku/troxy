#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
from collections import namedtuple
from enum import IntEnum
from pathlib import Path

from plib.utils.builders import FluidMixin
from plib.utils.datetime import parse_datetime_str
from translt import Mapping, DirectMapping
from translt.fieldelms import SequenceTransition, DictionaryTransition, AttributeTransition, \
    NamedAttributeTransition, ObjectGetter, ObjectSetter, ContextGetter, ContextSetter, IndexGetter, \
    IndexSetter, Const, Store, ValueReference
from translt.translators import BooleanTranslator, StringTranslator, FloatTranslator, IntegerTranslator, \
    ContainerTranslator, InputMode, OutputMode, ScalarTranslator


# TODO: Replace generators with cntxt.write() interface. - But: Items have to be processed one after the other
#       when it is not clear how many items are present.
# TODO: Performance seems to be quite horrible, especially for YAML. What can change during run time?
#       Use a parent and notification mechanism like in intrpt and prepare list etc. and only recalulate
#       them if necessary? Currently, fields are regarded as strategies for field formats but there are too
#       many hooks. Would it be better to play ping pong between formats and fields, that is, to split the
#       control flow between them? I would suppose that most fields do not need that many possibilities to
#       intercept the processing driven by the formats. Would it help to create a context per field and to
#       hand over some of the decisions, parts of the control flow to these contexts? Field formats as strategies?
# TODO: Precalculated keys / indexers for Flat and better integration of Hier (avoid unnecessary conversions)?
# TODO: Improve handling of unexpected situations and errors. For instance, it should be (optionally) possible
#       to raise an error if unknown keys or attributes are present in a record.
# TODO: Optional vs. mandatory elements or just cardinality...
#       What about values? object and record structure -> translt; values -> intrpt?
#       Also with regard to indexers, maybe their is a fundamental project toward graph structures somewhere...
#       Scalars are just typed terminals.
# TODO: Init methods, e.g. init_whatever( arg0, arg1, ... ): store parsed values and call method after parsing (cf. intrpt context)
# TODO: Remove Field appendix? Could be added via import or field.<name> if conflicts with intrpt arise.
# TODO: Handling of nulls: Optionally write all subfields although a container is None. (cf. mandatory elements)
# TODO: Fields vs. translators: Move data from translators to fields and create translator singletons implementing
#       different strategies for instance for unbound objects?
# TODO: Fields vs. formats: Put the control flow into the fields and leave formats as strategies?
# TODO: Exactly one element for each index or groups of elements?
# TODO: Logging and different formatting (e.g. record field structure, object structure according to transitions, ...) for debugging.
# TODO: Chains of field references instead of composites? Or (only) chains of transitions/value processors?
# TODO: Introduce paths? Direct: obj.<attrname>; Function: obj.getwhat( <index> )[ <index2>].get( const ).<attrname>;
#       Field structure: field[ <index> ][ <index2> ][ const ].<attrname>
# TODO: Add blocks of field and possibilities to add comments and space.
# TODO: Make default for elements configurable (e.g. attribute() in composites), either via field, via config
#       (and field integration), or via start_elementgroup or something.
# TODO: Merge of records stored in the meta formats Hier or Flat.
# TODO: Dynamic record mappings based on the type of objects instead of static field descriptions.
#       FlatMapping for Hier -> Hier to Flat transformator; and Flat to Hier
# TODO: Dynamic fields: Not based on static structure but on types. (Only for output updates and basic types)
class FieldMetaType(IntEnum):
    int        = 1
    float      = 2
    string     = 3
    bool       = 4
    sequence   = 5
    dictionary = 6



class RecordField(FluidMixin):
    __slots__ = ()

    @property
    @abstractmethod
    def metatype(self):
        ...

    @abstractmethod
    def get_translator(self):
        ...

    class CallingContext( namedtuple( 'CallerInfo', 'field indices') ):
        __slots__ = ()

        @property
        def element(self):
            return self.field.get_translator().get_element( self.indices[ 0 ] )

    def _call(self, sub, indices):
        return sub.caller( self.CallingContext( self, indices ) )

    @classmethod
    def _get_caller_config(cls, caller):
        return caller.field.get_config() if isinstance( caller, cls.CallingContext ) else None

    def end(self):
        return self._caller.field if isinstance( self._caller, self.CallingContext ) else self._caller

    def name(self, name):
        assert name is None or len( self._caller.indices )==1
        for i in self._caller.indices:
            self._caller.field.elementname( i, name )
        return self

    def integrate(self):
        return self.name( None )

    def inputmode(self, mode, constkeyword=None):
        for i in self._caller.indices:
            self._caller.field.element_inputmode( i, mode )
            if constkeyword is not None:
                self._caller.field.element_constructorkeyword( i, constkeyword )
        return self

    def no_input(self):
        return self.inputmode( InputMode.no_input )

    def update(self):
        return self.inputmode( InputMode.update )

    def set(self):
        return self.inputmode( InputMode.set )

    def construct(self, keyword=None):
        return self.inputmode( InputMode.construct, keyword )

    def outputmode(self, mode):
        for i in self._caller.indices:
            self._caller.field.element_outputmode( i, mode )
        return self

    def no_output(self):
        return self.outputmode( OutputMode.no_output )

    def write(self):
        return self.outputmode( OutputMode.write )

    def attribute(self, name=None, decorator=None):
        assert name is None or len( self._caller.indices )==1
        for i in self._caller.indices:
            self._caller.field.attribute_element( i, name, decorator )
        return self

    def constant(self, value):
        self._caller.element.value_generator = Const( value )
        return self

    def reference(self, src):
        self._caller.element.value_generator = ValueReference( src )
        return self

    def getter(self, getter):
        self._caller.element.getter = getter
        return self

    def setter(self, setter):
        self._caller.element.setter = setter
        return self.set()

    def objectgetter(self, getter):
        return self.getter( ObjectGetter( getter ) )

    def objectsetter(self, setter):
        return self.setter( ObjectSetter( setter ) )

    def indexgetter(self, getter):
        return self.getter( IndexGetter( getter ) )

    def indexsetter(self, setter):
        return self.setter( IndexSetter( setter ) )

    def contextgetter(self, getter):
        return self.getter( ContextGetter( getter ) )

    def contextsetter(self, setter):
        return self.setter( ContextSetter( setter ) )

    def store(self, dest=None, oninp=True, onout=True):
        self._caller.element.value_processor = Store( dest, oninp, onout )
        return self

    def category(self, value):
        self._caller.element.category = value
        return self


class ScalarField(RecordField):
    __slots__ = ()

    class Configurator(RecordField):
        @property
        def metatype(self):
            raise NotImplementedError( 'Not supported.' )

        def get_translator(self):
            raise NotImplementedError( 'Not supported.' )

    configurator = Configurator()

    @classmethod
    def caller(cls, caller):
        config = cls._get_caller_config( caller )
        configurator = config.create_scalarconfigurator( cls ) if config is not None else cls.configurator
        return configurator.caller( caller )


class IntegerField(ScalarField):
    __slots__ = ()

    metatype = FieldMetaType.int

    @classmethod
    def get_translator(cls):
        return IntegerTranslator


class FloatField(ScalarField):
    __slots__ = ()

    metatype = FieldMetaType.float

    @classmethod
    def get_translator(cls):
        return FloatTranslator


class StringField(ScalarField):
    __slots__ = ()

    metatype = FieldMetaType.string

    @classmethod
    def get_translator(cls):
        return StringTranslator


class BooleanField(ScalarField):
    __slots__ = ()

    metatype = FieldMetaType.bool

    @classmethod
    def get_translator(cls):
        return BooleanTranslator


class StringConverter(StringField, ScalarTranslator):
    def __init__(self, factory, tostr=str):
        self.factory = factory
        self.tostr   = str

    def get_translator(self):
        return self

    def create_object(self, cntxt, value):
        return self.factory( value )

    def get_value(self, obj, path=None):
        return self.tostr( super().get_value( obj, path ) )

PathField     = StringConverter( Path )
DateTimeField = StringConverter( parse_datetime_str )


class ContainerField(RecordField):
    def __init__(self, translt, config):
        super().__init__()

        self._config  = config
        self._translt = translt

    def get_config(self):
        return self._config

    def get_translator(self):
        return self._translt

    def type(self, typ):
        self._translt.factory = typ
        return self

    def name_processor(self, proc):
        self._translt.name_proc = proc
        return self

    def elementname(self, index, name):
        raise NotImplementedError( 'Element names are not supported by {}.'.format( type( self ) ) )

    def element_constructorkeyword(self, index, keyword):
        self._translt.set_constructorkeyword( index, keyword )
        return self

    def element_inputmode(self, index, inpmod):
        self._translt.set_inputmode( index, inpmod )
        return self

    def element_outputmode(self, index, outmod):
        self._translt.set_outputmode( index, outmod )
        return self

    def add_field(self, field, *index):
        return self.add_fieldgroup( field, index )

    def add_fieldgroup(self, field, indices):
        assert indices

        for index in indices:
            element = self._config.create_element_translator( field )

            self._translt.set_element( index, element )
            self._init_element( index, element )

        return self

    def _init_element(self, index, element):
        pass

    def configure(self, index):
        return self._call( self._translt.get_element( index ).subfield, (index,) )

    def init_field(self, field, *index):
        return self.init_fieldgroup( field, index )

    def init_fieldgroup(self, field, indices):
        return self.add_fieldgroup( field, indices )._call( field, indices )

    def start_integer(self, *index):
        return self.init_fieldgroup( self._config.create_integer(), index )

    def integer(self, *index):
        return self.start_integer( *index ).end()

    def start_float(self, *index):
        return self.init_fieldgroup( self._config.create_float(), index )

    def float(self, *index):
        return self.start_float( *index ).end()

    def start_string(self, *index):
        return self.init_fieldgroup( self._config.create_string(), index )

    def string(self, *index):
        return self.start_string( *index ).end()

    def start_boolean(self, *index):
        return self.init_fieldgroup( self._config.create_boolean(), index )

    def boolean(self, *index):
        return self.start_boolean( *index ).end()

    def start_sequence(self, *index):
        return self.init_fieldgroup( self._config.create_sequence(), index )

    def start_namedsequence(self, *index):
        return self.init_fieldgroup( self._config.create_namedsequence(), index )

    def start_dictionary(self, *index):
        return self.init_fieldgroup( self._config.create_dictionary(), index )

    def start_composite(self, *index):
        return self.init_fieldgroup( self._config.create_composite(), index )



class DirectContainerField(ContainerField, ContainerTranslator):
    metatype = FieldMetaType.dictionary

    def __init__(self, config):
        ContainerField.__init__( self, self, config )
        ContainerTranslator.__init__( self )

    def process_object(self, cntxt, obj, iterable):
        return obj

    def creating_subfields(self, cntxt, obj, avail_names=None):
        yield from ()

    def updating_subfields(self, cntxt, obj, avail_names=None):
        yield from ()

    def prepare_subfield_input(self, cntxt, index, subfldref, obj):
        return subfldref, None

    def process_subfield_input(self, cntxt, index, subfldref, obj):
        return obj

    def finish_subfield_input(self, cntxt, index, subfldref):
        pass

    def prepare_subfield_output(self, cntxt, index, subfldref, obj):
        return subfldref, obj

    def finish_subfield_output(self, cntxt, index, subfldref, obj):
        pass



class CollectionField(ContainerField):
    def _init_element(self, index, element):
        element.getter = self._default_transition
        element.setter = self._default_transition

        if index is None or self._translt.default_element is None:
            inpmod = InputMode.construct
            outmod = OutputMode.write
        else:
            inpmod = self._translt.default_inputmode
            outmod = self._translt.default_outputmode

        self._translt.set_inputmode( index, inpmod )
        self._translt.set_outputmode( index, outmod )

    @property
    @abstractmethod
    def _default_transition(self):
        ...

    def add_fieldgroup(self, field, indices):
        if len( indices )==0:
            indices = (None,)

        return super().add_fieldgroup( field, indices )

    def _call(self, field, indices):
        if len( indices )==0:
            indices = (None,)

        return super()._call( field, indices )



class SequenceField(CollectionField):
    metatype = FieldMetaType.sequence

    def __init__(self, config):
        CollectionField.__init__( self, self._create_translator( config ), config )

    def _create_translator(self, config):
        return config.create_sequence_translator()

    @property
    def _default_transition(self):
        return SequenceTransition

    def max_count(self, cnt):
        self._translt.max_count = cnt
        return self



class NamingFieldMixin(metaclass=ABCMeta):
    __slots__ = ()

    def indexoffset(self, offset):
        self.get_translator().index_offset = offset
        return self

    def elementname(self, index, name):
        self.get_translator().namegenerator[ index ] = name
        return self

    def integrate_element(self, index):
        return self.elementname( index, None )

    def elements_namepattern(self, pattern, inverse=None):
        self.get_translator().namegenerator.set_default_pattern( pattern, inverse )
        return self

    def integrate_all_elements(self):
        return self.elements_namepattern( None )



class NamedSequenceField(NamingFieldMixin, SequenceField):
    metatype = FieldMetaType.dictionary

    def __init__(self, config):
        ContainerField.__init__( self, self._create_translator( config ), config )

    def _create_translator(self, config):
        return config.create_namedsequence_translator()



class DictionaryField(NamingFieldMixin, CollectionField):
    metatype = FieldMetaType.dictionary

    def __init__(self, config):
        CollectionField.__init__( self, self._create_translator( config ), config )

    def _create_translator(self, config):
        return config.create_dictionary_translator()

    @property
    def _default_transition(self):
        return DictionaryTransition

    def input_keyprocessor(self, proc):
        self._translt.input_keyprocessor = proc
        return self

    def output_keyprocessor(self, proc):
        self._translt.output_keyprocessor = proc
        return self



class GenericNamedtupleField(DictionaryField):
    metatype = FieldMetaType.dictionary

    def _create_translator(self, config):
        return config.create_genericnamedtuple_translator()

    @property
    def _default_transition(self):
        return AttributeTransition

    def typename(self, name):
        self._translt.typename = name
        return self



class CompositeField(NamingFieldMixin, ContainerField):
    metatype = FieldMetaType.dictionary

    def __init__(self, factory, config):
        ContainerField.__init__( self, self._create_translator( factory, config ), config )

    def _create_translator(self, factory, config):
        return config.create_composite_translator( factory )

    def _init_element(self, index, element):
        self._translt.set_inputmode( index, InputMode.update )
        self._translt.set_outputmode( index, OutputMode.write )

    def attribute_element(self, index, name, decorator):
        element = self._translt.get_element( index )

        trans = AttributeTransition if name is None else NamedAttributeTransition( name )

        if decorator!=None:
            trans = decorator( trans )

        element.getter = trans
        element.setter = trans

        self._translt.set_inputmode( index, InputMode.set )
        self._translt.set_outputmode( index, OutputMode.write )

        return self



class NestedAttributeMapping(Mapping, DirectMapping):
    def __init__(self, attr_name, mapping):
        self.attr_name = attr_name
        self.mapping   = mapping

    def read_from(self, src):
        return self.mapping.read_from( src )

    def read_to(self, obj, src):
        setattr( obj, self.attr_name, self.read_from( src ) )

    def write_to(self, snk, obj):
        self.mapping.write_to( snk, getattr( obj, self.attr_name ) if obj is not None else None )


class AttributeMappingList(Mapping, DirectMapping, list):
    def add_nested(self, attr_name, mapping):
        self.append( NestedAttributeMapping( attr_name, mapping ) )
        return self

    def read_from(self, src):
        return [ m.read_from( src ) for m in self ]

    def read_to(self, obj, src):
        for m in self:
            m.read_to( obj, src )

    def write_to(self, snk, obj):
        for m in self:
            m.write_to( snk, obj )
