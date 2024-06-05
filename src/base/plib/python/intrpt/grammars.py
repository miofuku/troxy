#!/usr/bin/env python3.4

from intrpt.exprs import EventJoint, Scope, Variable, Constant, Sequence, Choice, Selection
from intrpt.indexers import ConstantIndexer, VariableIndexer, SequenceIndexer, ChoiceIndexer, ScopeIndexer
from intrpt.parsers import ScopeParseContext, ConstantParser, VariableParser, SequenceParser, ChoiceParser, \
    RepetitionParser, UnboundParser
from intrpt.valacts import ValueActionList
from intrpt.valprocs import ValueProcessorChain


class StandardGrammarFamily:
    @classmethod
    def get_default_destination(self, name):
        return name.replace( '-', '_' )

    _default_variable_processors = ValueProcessorChain()

    @classmethod
    def get_default_variable_processors(cls):
        return cls._default_variable_processors

    @classmethod
    def create_event_joint(cls, typeno):
        return EventJoint( typeno )

    @classmethod
    def create_value_action_collection(cls):
        return ValueActionList()

    @classmethod
    def create_value_processor_collection(cls):
        return ValueProcessorChain()

    @classmethod
    def create_scope(cls, expr):
        return Scope( expr, cls )

    @classmethod
    def create_scope_parse_context(cls, cntxt, scope):
        return ScopeParseContext( cntxt, scope, cls )

    @classmethod
    def create_variable(cls, name):
        return Variable( name, cls )

    @classmethod
    def create_constant(cls, value):
        return Constant( value, cls )

    @classmethod
    def create_sequence(cls):
        return Sequence( cls )

    @classmethod
    def create_choice(cls):
        return Choice( cls )

    @classmethod
    def create_selection(cls, name):
        return Selection( name, cls )

    @classmethod
    def create_constant_parser(cls, expr, value):
        return ConstantParser( expr, value, cls )

    @classmethod
    def create_variable_parser(cls, expr, name):
        return VariableParser( expr, name, cls )

    @classmethod
    def create_sequence_parser(cls, expr):
        return SequenceParser( expr, cls )

    @classmethod
    def create_choice_parser(cls, expr):
        return ChoiceParser( expr, cls )

    @classmethod
    def create_repetition_parser(cls, expr, subparser):
        return RepetitionParser( expr, subparser, cls )

    @classmethod
    def create_unbound_parser(cls, expr, subparser):
        return UnboundParser( expr, subparser, cls )

    @classmethod
    def create_constant_indexer(cls, principal, parser):
        return ConstantIndexer( principal, parser )

    @classmethod
    def create_variable_indexer(cls, principal, parser):
        return VariableIndexer( principal, parser )

    @classmethod
    def create_sequence_indexer(cls, principal, parser):
        return SequenceIndexer( principal, parser )

    @classmethod
    def create_choice_indexer(cls, principal, parser):
        return ChoiceIndexer( principal, parser )

    @classmethod
    def create_scope_indexer(cls, principal):
        return ScopeIndexer( principal )
