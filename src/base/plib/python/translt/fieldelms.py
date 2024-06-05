#!/usr/bin/env python3.4

from abc import ABC, abstractmethod, ABCMeta

from plib.utils.builders import Reference
from plib.utils import Value


class FieldContext(ABC):
    @property
    @abstractmethod
    def name_procs(self):
        ...

    @property
    @abstractmethod
    def field_store(self):
        ...


class FieldnameProcessor:
    Empty = None

    __slots__ = ()

    def __call__(self, field_name_parts, category=None):
        return field_name_parts

    def revoke(self, field_name, category=None):
        return field_name

    def combine(self, proc):
        if proc is None or proc==FieldnameProcessor.Empty:
            return self
        elif self==FieldnameProcessor.Empty:
            return proc
        else:
            return self._do_combine( proc )

    def for_index(self, index, cntxt):
        return self

    def _do_combine(self, proc):
        return FieldProcessorChain( (proc, self) )

FieldnameProcessor.Empty = FieldnameProcessor()


class FieldProcessorChain(FieldnameProcessor):
    __slots__ = 'procs'

    def __init__(self, procs):
        self.procs = procs

    def __call__(self, field_name_parts, category):
        for proc in self.procs:
            proc( field_name_parts, category )
        return field_name_parts

    def revoke(self, field_name, category):
        for proc in self.procs[ ::-1 ]:
            field_name = proc.revoke( field_name, category )
        return field_name

    def for_index(self, index, cntxt):
        return self.__class__( [ proc.for_index( index, cntxt ) for proc in self.procs ] )


class PrefixProcessor(FieldnameProcessor):
    PlainIndex = None

    Undefined = object()

    __slots__ = 'prefix', 'category'

    def __init__(self, prefix, category=Undefined):
        self.prefix   = prefix
        self.category = category

    def __call__(self, field_name_parts, category=None):
        if self.category==PrefixProcessor.Undefined or category==self.category:
            field_name_parts.insert( 0, self.prefix )
        return field_name_parts

    def revoke(self, field_name, category=None):
        if self.category==PrefixProcessor.Undefined or category==self.category:
            field_name = field_name[ len( self.prefix ): ]
        return field_name

    def for_index(self, index, cntxt):
        return self.__class__( self.prefix.format( index, cntxt ), self.category )

    def _do_combine(self, proc):
        if type(proc)==self.__class__ and proc.category==self.category:
            return self.__class__( self.prefix+proc.prefix, self.category )
        else:
            return super()._do_combine( proc )

PrefixProcessor.PlainIndex = PrefixProcessor( '{}' )



class ValueProcessor:
    Empty = None

    __slots__ = ()

    def process_input(self, cntxt, field, index, obj):
        return obj

    def process_output(self, cntxt, field, index, obj):
        return obj

ValueProcessor.Empty = ValueProcessor()


class ValueProcChain(ValueProcessor):
    __slots__ = 'procs'

    def __init__(self, procs):
        self.procs = procs

    def process_input(self, cntxt, field, index, obj):
        for proc in self.procs:
            obj = proc.process_input( cntxt, field, index, obj )
        return obj

    def process_ouptut(self, cntxt, field, index, obj):
        for proc in self.procs:
            obj = proc.process_output( cntxt, field, index, obj )
        return obj



class Store(ValueProcessor):
    __slots__ = 'dest', 'on_input', 'on_output'

    def __init__(self, dest=None, oninp=True, onout=True):
        self.dest      = Reference.dest( dest, self )
        self.on_input  = oninp
        self.on_output = onout

    def process_input(self, cntxt, field, index, obj):
        if self.on_input:
            cntxt.field_store[ self.dest ] = obj
        return obj

    def process_output(self, cntxt, field, index, obj):
        if self.on_output:
            cntxt.field_store[ self.dest ] = obj
        return obj

    def get_value(self, cntxt):
        return cntxt.field_store[ self.dest ]



class ValueGenerator:
    __slots__ = ()

    def get_input_value(self, cntxt, field, index, obj):
        raise NotImplementedError()

    def get_output_value(self, cntxt, field, index, obj):
        raise NotImplementedError()


class Const(ValueGenerator):
    __slots__ = 'value'

    def __init__(self, value):
        self.value = value

    def get_input_value(self, cntxt, field, index, obj):
        return self.value

    def get_output_value(self, cntxt, field, index, obj):
        return self.value


class ValueReference(ValueGenerator):
    __slots__ = 'src'

    def __init__(self, src):
        self.src = Reference.source( src )

    def get_input_value(self, cntxt, field, index, obj):
        return cntxt.field_store[ self.src ]

    def get_output_value(self, cntxt, field, index, obj):
        return cntxt.field_store[ self.src ]


class Function(ValueGenerator):
    __slots__ = 'inpfunc', 'outfunc'

    def __init__(self, inpfunc, outfunc):
        self.inpfunc = inpfunc
        self.outfunc = outfunc

    def get_input_value(self, cntxt, field, index, obj):
        return self.inpfunc( obj )

    def get_output_value(self, cntxt, field, index, obj):
        return self.outfunc( obj )



class ObjectTransition(metaclass=ABCMeta):
    __slots__ = ()

    @classmethod
    def has_value(cls, cntxt, field, index, obj):
        return True

    @abstractmethod
    def get_value(self, cntxt, field, index, obj):
        ...

    @abstractmethod
    def set_value(self, cntxt, field, index, obj, value):
        ...



class SequenceTransition(ObjectTransition):
    __slots__ = ()

    @classmethod
    def has_value(cls, cntxt, field, index, obj):
        return index<len( obj )

    @classmethod
    def get_value(cls, cntxt, field, index, obj):
        return obj[ index ]

    @classmethod
    def set_value(cls, cntxt, field, index, obj, value):
        obj[ index ] = value



class DictionaryTransition(ObjectTransition):
    __slots__ = ()

    @classmethod
    def has_value(cls, cntxt, field, index, obj):
        return index in obj

    @classmethod
    def get_value(cls, cntxt, field, index, obj):
        return obj[ index ]

    @classmethod
    def set_value(cls, cntxt, field, index, obj, value):
        obj[ index ] = value



class AttributeTransition(ObjectTransition):
    __slots__ = ()

    @classmethod
    def has_value(cls, cntxt, field, index, obj):
#        return hasattr( obj, index )
        return True

    @classmethod
    def get_value(cls, cntxt, field, index, obj):
        return getattr( obj, index )

    @classmethod
    def set_value(cls, cntxt, field, index, obj, value):
        setattr( obj, index, value )



class NamedAttributeTransition(ObjectTransition):
    __slots__ = 'attrname'

    def __init__(self, attrname):
        self.attrname = attrname

    def has_value(self, cntxt, field, index, obj):
#        return hasattr( obj, self.attrname )
        return True

    def get_value(self, cntxt, field, index, obj):
        return getattr( obj, self.attrname )

    def set_value(self, cntxt, field, index, obj, value):
        setattr( obj, self.attrname, value )


class OutputDefined(ObjectTransition):
    __slots__ = 'transition'

    def __init__(self, transition):
        self.transition = transition

    def has_value(self, cntxt, field, index, obj):
        return self.transition.has_value( cntxt, field, index, obj ) and \
               self.get_value( cntxt, field, index, obj)!=Value.Undefined

    def get_value(self, cntxt, field, index, obj):
        return self.transition.get_value( cntxt, field, index, obj )

    def set_value(self, cntxt, field, index, obj, value):
        self.transition.set_value( cntxt, field, index, obj, value )


class ContextGetter:
    __slots__ = 'getter'

    def __init__(self, getter):
        self.getter = getter

    def has_value(self, cntxt, field, index, obj):
        return True

    def get_value(self, cntxt, field, index, obj):
        return self.getter( cntxt, field, index, obj )


class ContextSetter:
    __slots__ = 'setter'

    def __init__(self, setter):
        self.setter = setter

    def set_value(self, cntxt, field, index, obj, value):
        return self.setter( cntxt, field, index, obj, value )



class ObjectGetter:
    __slots__ = 'getter'

    def __init__(self, getter):
        self.getter = getter

    def has_value(self, cntxt, field, index, obj):
        return True

    def get_value(self, cntxt, field, index, obj):
        return self.getter( obj )


class ObjectSetter:
    __slots__ = 'setter'

    def __init__(self, setter):
        self.setter = setter

    def set_value(self, cntxt, field, index, obj, value):
        return self.setter( obj, value )



class IndexGetter:
    __slots__ = 'getter'

    def __init__(self, getter):
        self.getter = getter

    def has_value(self, cntxt, field, index, obj):
        return True

    def get_value(self, cntxt, field, index, obj):
        return self.getter( obj, index )


class IndexSetter:
    __slots__ = 'setter'

    def __init__(self, setter):
        self.setter = setter

    def set_value(self, cntxt, field, index, obj, value):
        return self.setter( obj, index, value )



class ElementTranslator:
    __slots__ = 'subfield', 'getter', 'setter', 'name_processor', 'value_processor', 'value_generator', 'category'

    def __init__(self, subfield):
        super().__init__()

        assert subfield is not None

        self.subfield         = subfield
        self.name_processor   = None

        # TODO: Combine to value processing chain (when elements are in charge of the control flow).
        #       Chain with different stages like events for registering? Pass kind of event to handler?
        self.value_processor  = None
        self.getter           = None
        self.setter           = None

        self.value_generator  = None

        self.category = None

    def has_value(self, cntxt, field, index, obj):
        if self.getter is not None and obj is not None:
            return self.getter.has_value( cntxt, field, index, obj )
        else:
            return True

    def get_value(self, cntxt, field, index, obj):
        if self.getter is not None and obj is not None:
            value = self.getter.get_value( cntxt, field, index, obj )
        else:
            value = obj

        # TODO: Aargh!
        if isinstance( self.value_processor, Store ):
            self.value_processor.process_input( cntxt, field, index, value )

        return value

    def set_value(self, cntxt, field, index, obj, value):
        self.setter.set_value( cntxt, field, index, obj, value )

    def _start_processing(self, cntxt, field, index):
        if self.name_processor:
            cntxt.name_procs.append( self.name_processor )

    def apply_name_proc(self, cntxt, field, field_name_parts, name_proc ):
        name_proc( field_name_parts, self.category )

    def _finish_processing(self, cntxt, field, index):
        if self.name_processor:
            cntxt.name_procs.pop()

    def prepare_input(self, cntxt, field, index, obj):
        if self.value_generator:
            return None, self.value_generator.get_input_value( cntxt, field, index, obj )
        else:
            self._start_processing( cntxt, field, index )
            return self.subfield, None

    def process_input(self, cntxt, field, index, obj):
        if self.value_processor:
            return self.value_processor.process_input( cntxt, field, index, obj )
        else:
            return obj

    def finish_input(self, cntxt, field, index):
        self._finish_processing( cntxt, field, index )

    def prepare_output(self, cntxt, field, index, obj):
        if self.value_generator:
            return self.subfield, self.value_generator.get_output_value( cntxt, field, index, obj )
        else:
            self._start_processing( cntxt, field, index )
            if self.value_processor:
                obj = self.value_processor.process_output( cntxt, field, index, obj )
            return self.subfield, obj

    def finish_output(self, cntxt, field, index, obj):
        self._finish_processing( cntxt, field, index )
