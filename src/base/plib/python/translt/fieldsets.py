#!/usr/bin/env python3.4

from translt.fieldelms import ElementTranslator
from translt.fields import IntegerField, FloatField, StringField, BooleanField, SequenceField, \
    NamedSequenceField, DictionaryField, CompositeField, ScalarField, GenericNamedtupleField
from translt.translators import IntegerTranslator, FloatTranslator, StringTranslator, BooleanTranslator, \
    SequenceTranslator, NamedSequenceTranslator, DictionaryTranslator, CompositeTranslator, \
    GenericNamedtupleTranslator


class RecordFieldSet:
    @classmethod
    def create_integer(cls):
        return IntegerField

    @classmethod
    def create_float(cls):
        return FloatField

    @classmethod
    def create_string(cls):
        return StringField

    @classmethod
    def create_boolean(cls):
        return BooleanField

    @classmethod
    def create_sequence(cls):
        return SequenceField( cls )

    @classmethod
    def create_namedsequence(cls):
        return NamedSequenceField( cls )

    @classmethod
    def create_dictionary(cls):
        return DictionaryField( cls )

    @classmethod
    def create_genericnamedtuple(cls):
        return GenericNamedtupleField( cls )

    @classmethod
    def create_composite(cls, factory=None):
        return CompositeField( factory, cls )

    @classmethod
    def create_integer_translator(cls):
        return IntegerTranslator

    @classmethod
    def create_float_translator(cls):
        return FloatTranslator

    @classmethod
    def create_string_translator(cls):
        return StringTranslator

    @classmethod
    def create_boolean_translator(cls):
        return BooleanTranslator

    @classmethod
    def create_sequence_translator(cls):
        return SequenceTranslator()

    @classmethod
    def create_namedsequence_translator(cls):
        return NamedSequenceTranslator()

    @classmethod
    def create_dictionary_translator(cls):
        return DictionaryTranslator()

    @classmethod
    def create_genericnamedtuple_translator(cls):
        return GenericNamedtupleTranslator()

    @classmethod
    def create_composite_translator(cls, factory):
        return CompositeTranslator( factory )

    @classmethod
    def create_element_translator(cls, target):
        return ElementTranslator( target )

    @classmethod
    def create_scalarconfigurator(cls, inner):
        return ScalarField.configurator
