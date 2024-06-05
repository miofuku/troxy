#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
from itertools import chain

from consl.cmdfuncs import NestedNameMixin
from consl.docs import DocGenerator, ParameterDoc, CommandDoc
from consl.parsers import FlagGroupParser
from intrpt.exprfuncs import NestedCompareLowercaseMixin, CompareLowercaseMixin, DefaultValueMixin, ConstValueMixin, \
    NestedValueProcessorMixin, NestedDestinationMixin, NestedConstValueMixin, NestedDefaultValueMixin
from intrpt.exprs import Constant, Variable, Sequence, Choice, Selection, Terminal, \
    NonterminalMixin, GroupMixin, SubgroupsMixin, SurrogateExpression, Nonterminal
from intrpt.format import ExprFormatter, FormatSequence
from intrpt.grammars import StandardGrammarFamily
from intrpt.utils import ReferenceCounter
from intrpt.valprocs import StartsWithChecker
from plib.utils import Value


#------------------------------------------------------------------------------
# Configuration and central factory
#-----

class CommandGrammarFamily(StandardGrammarFamily):
    @classmethod
    def get_flaggroup_prefix(cls):
        return '-'

    @classmethod
    def get_option_key(cls, name):
        return ( '-' if len( name )==1 else '--' ) + name

#    def create_default_processors():
#        defprocs = []
#        checked  = []
#        for p in list_of_default_processors[ ::-1 ]:
#            if not [x for x in checked if p.startswith( x )]:
#                defprocs.append( StartsWithChecker( p, True ) )
#                checked.append( p )
#        return defprocs

    StandardGrammarFamily.get_default_variable_processors().add_processor( StartsWithChecker( '-', True ) )

    @classmethod
    def create_paramdoc(cls, usage, description):
        return ParameterDoc( usage, description )

    @classmethod
    def create_cmddoc(cls, name, usage, abstract, description):
        return CommandDoc( name, usage, abstract, description )

    @classmethod
    def create_format_instruction(cls):
        return FormatSequence()

    @classmethod
    def create_constant(cls, value):
        return ConstantElement( value, cls )

    @classmethod
    def create_variable(cls, name):
        return VariableElement( name, cls )

    @classmethod
    def create_sequence(cls):
        return SequenceElement( cls )

    @classmethod
    def create_choice(cls):
        return ChoiceElement( cls )

    @classmethod
    def create_selection(cls, name):
        return SelectionElement( name, cls ).compare_lowercase( True )

    @classmethod
    def create_subcommandgroup(cls):
        return SubcommandGroup( cls )

    @classmethod
    def create_optionkey(cls, name):
        return OptionKey( name, cls )

    @classmethod
    def create_flag(cls, name):
        return Flag( name, cls )

    @classmethod
    def create_variable_option(cls, name):
        return VariableOption( name, cls )

    @classmethod
    def create_option(cls, name):
        return Option( name, cls )

    @classmethod
    def create_switch(cls, name):
        return Switch( name, cls )

    @classmethod
    def create_flaggroup(cls):
        return FlagGroup( cls.get_flaggroup_prefix(), cls )

    @classmethod
    def create_flaggroup_parser(cls, principal, prefix):
        return FlagGroupParser( principal, prefix, cls )

    @classmethod
    def create_parameter(cls):
        return ParameterExpression( cls )

    @classmethod
    def create_command(cls, name):
        return CommandExpression( name, cls )


#-------------------------------------------------------------------------------
# Root classes
#----
# TODO: Documentation aspects could be factored out into an own builder like it was done for scopes.
#       That way, the structure of the documentation, i.e. which parts are necessary and can be configured,
#       becomes adjustable.
class CommandElement(metaclass=ABCMeta):
    default_grammar = CommandGrammarFamily

    def __init__(self):
        self._usage    = None
        self._extusage = None
        self._commands = ReferenceCounter()

    def usage(self, value):
        self._usage = value
        return self

    def start_usage(self):
        self._usage = self.get_config().create_format_instruction()
        return self.configure_usage()

    def configure_usage(self):
        return self._usage.caller( self )

    def get_usage(self):
        return self._usage

    def extended_usage(self, value):
        self._extusage = value
        return self

    def start_extended_usage(self):
        self._extusage = self.get_config().create_format_instruction()
        return self.configure_extended_usage()

    def configure_extended_usage(self):
        return self._extusage.caller( self )

    def get_extended_usage(self):
        return self._extusage

    def register_command(self, cmd):
        if self._commands.add( cmd ).count==1:
            self._command_added( cmd )

    def deregister_command(self, cmd):
        if self._commands.remove( cmd ).count==0:
            self._command_removed( cmd )

    def _command_added(self, cmd):
        pass

    def _command_removed(self, cmd):
        pass

    def apply_usage(self, exprfmt, forparamdoc=False):
        u = self._extusage if forparamdoc else self._usage

        if u is None:
            self._apply_default_usage( exprfmt, forparamdoc )
        elif isinstance( u, str ):
            exprfmt.item( u )
        else:
            exprfmt.apply( u )

    @abstractmethod
    def _apply_default_usage(self, exprfmt, forparamdoc):
        ...

    def apply_doc(self, cmddoc):
        pass

#    @abstractmethod
#    def get_config(self):
#        ...



class ParameterMixin(DocGenerator):
    def __init__(self):
        self._description = None

    @abstractmethod
    def get_name(self):
        ...

    def description(self, value):
        self._description = value
        return self

    def get_description(self):
        return self._description

    def apply_usage(self, exprfmt, forparamdoc=False):
        if not forparamdoc:
            super().apply_usage( exprfmt, forparamdoc=forparamdoc )

    def apply_doc(self, cmddoc):
        if self._description is not None:
            cmddoc.parameters.append( self.create_doc() )

        super().apply_doc( cmddoc )

    def create_doc(self):
        exprfmt = ExprFormatter()
        super().apply_usage( exprfmt, forparamdoc=True )

        return self.get_config().create_paramdoc( exprfmt.create_str(), self._description )

    def _command_added(self, cmd):
        cmd.register_parameter( self )
        super()._command_added( cmd )

    def _command_removed(self, cmd):
        super()._command_removed( cmd )
        cmd.deregister_parameter( self )

#    @abstractmethod
#    def get_config(self):
#        ...


#-------------------------------------------------------------------------------
# Terminals
#----

class ConstantElement(Constant, CommandElement):
    def __init__(self, value, config=None):
        CommandElement.__init__( self )
        Constant.__init__( self, value, config or self.default_grammar )

        self.compare_lowercase()

    # TODO: Expanded usage: (-o | --opt) <opt> -> -o <opt>, --opt <opt>
    def _apply_default_usage(self, exprfmt, forparamdoc):
        if forparamdoc and self.get_alternatives():
            exprfmt = exprfmt.start_choice()

            exprfmt.constant( self.get_value() )
            for alt in self.get_alternatives():
                exprfmt.constant( alt )

            exprfmt.end()
        else:
            exprfmt.cardinality( self.get_min_count(), self.get_max_count() ).constant( self.get_value() )



class VariableElement(Variable, CommandElement):
    def __init__(self, name, config=None):
        CommandElement.__init__( self )
        Variable.__init__( self, name, config or self.default_grammar )

    def _apply_default_usage(self, exprfmt, forparamdoc):
        exprfmt.cardinality( self.get_min_count(), self.get_max_count() ).variable( self.get_name() )


#-------------------------------------------------------------------------------
# Nonterminals
#----

class NonterminalElement(CommandElement):
    @property
    def _is_command(self):
        return False

    def _command_added(self, cmd):
        super()._command_added( cmd )

        if not self._is_command:
            for sub in self._all_subexprs:
                sub.register_command( cmd )

    def _command_removed(self, cmd):
        super()._command_removed( cmd )

        if not self._is_command:
            for sub in self._all_subexprs:
                sub.deregister_command( cmd )

    def _register_expression(self, expr):
        super()._register_expression( expr )

        if self._is_command:
            expr.register_command( self )
        else:
            for ref in self._commands:
                expr.register_command( ref.object )

    def _deregister_expression(self, expr):
        if self._is_command:
            expr.deregister_command( self )
        else:
            for ref in self._commands:
                expr.deregister_command( ref.object )

        super()._deregister_expression( expr )

    def _apply_default_usage(self, exprfmt, forparamdoc):
        cnt = len( self._all_subexprs )

        if cnt:
            if cnt==1:
                self._all_subexprs[ 0 ].apply_usage( exprfmt, forparamdoc )
            else:
                exprfmt = self.cardinality( self.get_min_count(), self.get_max_count() )._start_group( exprfmt )
                for sub in self._all_subexprs:
                    sub.apply_usage( exprfmt, forparamdoc )
                exprfmt.end()

    def apply_doc(self, cmddoc):
        for sub in self._all_subexprs:
            sub.apply_doc( cmddoc )

#    @abstractmethod
#    def get_expressions(self):
#        ...

    @abstractmethod
    def _start_group(self, exprfmt):
        ...

#    @abstractmethod
#    def get_min_count(self):
#        ...
#
#    @abstractmethod
#    def get_max_count(self):
#        ...


class SubgroupsElementMixin(metaclass=ABCMeta):
    __slots__ = ()

    def start_command(self, name):
        return self.init_expression( self.get_config().create_command( name ) )

    def start_subcommands(self):
        return self.init_expression( self.get_config().create_subcommandgroup() )

    def start_parameter(self):
        return self.init_expression( self.get_config().create_parameter() )

    def flag(self, name, *aliases, desc=None):
        return self.start_flag( name ).extend_aliases( aliases ).description( desc ).end()

    def start_flag(self, name, *aliases):
        return self.init_expression( self.get_config().create_flag( name ) )

    def variable_option(self, name, *aliases, desc=None):
        return self.start_variable_option( name ).extend_aliases( aliases ).description( desc ).end()

    def start_variable_option(self, name, *aliases):
        return self.init_expression( self.get_config().create_variable_option( name ).extend_aliases( aliases ) )

    def start_option(self, name, *aliases):
        return self.init_expression( self.get_config().create_option( name ).extend_aliases( aliases ) )

    def switch(self, on, off, aliases_on=(), aliases_off=(), desc=None, default_value=Value.Undefined):
        sw = self.start_switch( on ).on( on, *aliases_on ).off( off, *aliases_off ).description( desc )

        if default_value!=Value.Undefined:
            sw.default_value( default_value )

        return self

    def start_switch(self, name):
        return self.init_expression( self.get_config().create_switch( name ) )

    def start_flaggroup(self):
        return self.init_expression( self.get_config().create_flaggroup() )

    @abstractmethod
    def get_config(self):
        ...


class SequenceElement(NonterminalElement, Sequence, SubgroupsElementMixin):
    def __init__(self, config=None):
        Sequence.__init__( self, config or self.default_grammar )
        NonterminalElement.__init__( self )

    def _start_group(self, exprfmt):
        return exprfmt.start_sequence()



class ParameterExpression(ParameterMixin, SequenceElement, SubgroupsElementMixin):
    def __init__(self, config=None):
        ParameterMixin.__init__( self )
        SequenceElement.__init__( self, config )



class ChoiceElement(NonterminalElement, Choice, SubgroupsElementMixin):
    def __init__(self, config=None):
        Choice.__init__( self, config or self.default_grammar )
        NonterminalElement.__init__( self )

    def _start_group(self, exprfmt):
        return exprfmt.start_choice()



class SubcommandGroup(ChoiceElement):
    def __init__(self, config=None):
        super().__init__( config )

        self._cmdstitle = 'command'
        self._argstitle = 'args'

    def commands_placeholder(self, value):
        self._cmdstitle = value
        return self

    def get_commands_placeholder(self):
        return self._cmdstitle

    def arguments_placeholder(self, value):
        self._argstitle = value
        return self

    def get_arguments_placeholder(self):
        return self._argstitle


    def _apply_default_usage(self, exprfmt, forparamdoc):
        if self._cmdstitle is None:
            super()._apply_default_usage( exprfmt, forparamdoc )
        elif self._argstitle is None:
            exprfmt.variable( self._cmdstitle )
        else:
            exprfmt.start_sequence() \
                    .variable( self._cmdstitle ) \
                    .optional() \
                    .variable( self._argstitle ) \
                    .end()



class SelectionElement(NonterminalElement, Selection):
    def __init__(self, name, config=None):
        Selection.__init__( self, name, config or self.default_grammar )
        NonterminalElement.__init__( self )

    def _start_group(self, exprfmt):
        return exprfmt.start_choice()


class CommandExpression(SequenceElement, DocGenerator, NestedCompareLowercaseMixin):
    def __init__(self, name, config=None):
        SequenceElement.__init__( self, config )

        self._name_expr   = self._config.create_constant( name )
        self._abstract    = None
        self._description = None
        self._prepname    = False

        self._subcmds = {}
        self._params  = {}

        self.create_new_scope()
        self.prepend_name_element()

    @property
    def _nested_cmplower(self):
        return self._name_expr

    def configure_name(self):
        return self._name_expr.caller( self )

    def get_name(self):
        return self._name_expr.get_value()

    def alias(self, *alias):
        return self.extend_aliases( alias )

    def extend_aliases(self, aliases):
        self._name_expr.extend_alternatives( aliases )
        return self

    def get_aliases(self):
        return self._name_expr.get_alternatives()

    def abstract(self, value):
        self._abstract = value
        return self

    def get_abstract(self):
        return self._abstract

    def description(self, value):
        self._description = value
        return self

    def get_description(self):
        return self._description

    def prepend_name_element(self, value=True):
        if value!=self._prepname:
            self._prepname = value

            if value:
                self.prepend_expression( self._name_expr )
            else:
                self.remove_expression( self._name_expr )

        return self

    def get_prepend_name_element(self):
        return self._prepname

    @property
    def _is_command(self):
        return True

    # TODO: To reuse complete commands, a special method add_command(cmd, index=None) could be provided
    #       that registers the command not with its name but with the given index. But how often would
    #       that be used?
    def register_subcommand(self, subcmd):
        assert subcmd.get_name() not in self._subcmds
        self._subcmds[ subcmd.get_name() ] = subcmd

    def deregister_subcommand(self, subcmd):
        assert subcmd.get_name() in self._subcmds
        del self._subcmds[ subcmd.get_name() ]

    def register_parameter(self, param):
        assert param.get_name() not in self._params
        self._params[ param.get_name() ] = param

    def deregister_parameter(self, param):
        assert param.get_name() in self._params
        del self._params[ param.get_name() ]

    def _command_added(self, cmd):
        cmd.register_subcommand( self )
        super()._command_added( cmd )

    def _command_removed(self, cmd):
        super()._command_removed( cmd )
        cmd.deregister_subcommand( self )

    def apply_doc(self, cmddoc):
        cmddoc.subcommands.append( self.create_doc() )

    def create_doc(self, cmdpath=None):
        nameusg = False

        if cmdpath is None:
            namestr = self.get_name()
        else:
            namestr = ' '.join( cmdpath )

            # TODO: A hack... If there are more situations similar to this one, maybe a context or some
            # other mechanism for dynamic documentations have to be introduced.
            if self._name_expr.get_usage() is None:
                self._name_expr.usage( namestr )
                nameusg = True

        exprfmt = ExprFormatter()
        super().apply_usage( exprfmt )

        cmddoc = self._config.create_cmddoc( namestr, exprfmt.create_str(), self._abstract, self._description )

        if nameusg:
            self._name_expr.usage( None )

        for n in sorted( self._subcmds.keys() ):
            self._subcmds[ n ].apply_doc( cmddoc )
        for n in sorted( self._params.keys() ):
            self._params[ n ].apply_doc( cmddoc )

        return cmddoc

    def find_command(self, cmdpath):
        if not cmdpath:
            return self
        else:
            i, *rp = cmdpath

            if i not in self._subcmds:
                raise KeyError( i )

            return self._subcmds[ i ].find_command( rp )


#-------------------------------------------------------------------------------
# Options
#----

class OptionKeyBase(Terminal, CompareLowercaseMixin, ConstValueMixin, CommandElement):
    def __init__(self, name, config=None):
        if config is None:
            config = self.defaul_grammar

        Terminal.__init__( self, self._create_parser( config.get_option_key( name ), config ), config )
        ConstValueMixin.__init__( self )
        CompareLowercaseMixin.__init__( self )
        CommandElement.__init__( self )

        self._name = name
        self._aliases = []

    def _create_parser(self, value, config):
        return config.create_constant_parser( self, value )

    @property
    def _cmplower_processor_collection(self):
        return self._parser.processors

    def get_name(self):
        return self._name

    def get_decorated_name(self):
        return self._parser.value

    def alias(self, *alias):
        return self.extend_aliases( alias )

    def extend_aliases(self, aliases):
        if aliases:
            self._aliases.extend( aliases )

            alterns = [self._config.get_option_key( a ) for a in aliases]
            self._parser.alternatives.extend( alterns )
            self._parser.indexer.values_added( alterns )
        return self

    def get_aliases(self):
        return self._aliases

    def get_decorated_aliases(self):
        return self._parser.alternatives

    def flag(self):
        return self.value( True ).default_value( False )

    def as_unbound(self, value=True):
        raise NotImplementedError( 'Unsupported operation.' )

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        exprfmt.constant( self._parser.value )

    def _apply_default_usage(self, exprfmt, forparamdoc):
        if forparamdoc and self.get_decorated_aliases():
            exprfmt = exprfmt.start_choice()

            exprfmt.constant( self.get_decorated_name() )
            for a in self.get_decorated_aliases():
                exprfmt.constant( a )

            exprfmt = exprfmt.end()
        else:
            exprfmt.constant( self.get_decorated_name() )



class OptionKey(OptionKeyBase, DefaultValueMixin):
    def __init__(self, name, config=None):
        OptionKeyBase.__init__( self, name, config )
        DefaultValueMixin.__init__( self )

        self.destination( self._config.get_default_destination( name ) )

    @property
    def _default_value_joint(self):
        return self._parser.scope.get_default_value_joint()

    def destination(self, dest):
        DefaultValueMixin.destination( self, dest )
        ConstValueMixin.destination( self, dest )
        return self



class OptionBase(ParameterMixin, NonterminalElement, Nonterminal, NestedNameMixin):
    def __init__(self, name, config=None):
        if config is None:
            config = self.default_grammar

        Nonterminal.__init__( self, self._create_parser( config ), config )
        ParameterMixin.__init__( self )
        NonterminalElement.__init__( self )

        self._key = config.create_optionkey( name )

        self._add_expression( self._key )

        self.as_unbound().optional()

    def _create_parser(self, config):
        return config.create_sequence_parser( self )

    def _start_group(self, exprfmt):
        return exprfmt.start_sequence()

    def get_name(self):
        return self._key.get_name()

    @property
    def _nested_name(self):
        return self._key

    def configure_key(self):
        return self._key.caller( self )

    def get_key(self):
        return self._key

    def _apply_default_usage(self, exprfmt, forparamdoc):
        cnt = len( self._parser.subexprs )

        if not forparamdoc:
            exprfmt.cardinality( self.get_min_count(), self.get_max_count() )

        if cnt>1:
            exprfmt = exprfmt.start_sequence()

        self._apply_default_usage_for_subexprs( exprfmt, forparamdoc )

        if cnt>1:
            exprfmt.end()

    def _apply_default_usage_for_subexprs(self, exprfmt, forparamdoc):
        for sub in self._parser.subexprs:
            sub.apply_usage( exprfmt, forparamdoc )



class Flag(OptionBase, NestedValueProcessorMixin, NestedDestinationMixin, NestedCompareLowercaseMixin,
           NestedConstValueMixin, NestedDefaultValueMixin):
    def __init__(self, name, config=None):
        OptionBase.__init__( self, name, config )

        self._key.flag()

    def execute_actions(self, cntxt, expr, value):
        self._key._parser.actions( cntxt, expr, value )

    @property
    def _nested_cmplower(self):
        return self._key

    @property
    def _nested_value_processors(self):
        return self._key

    @property
    def _nested_destination(self):
        return self._key

    @property
    def _nested_const_value(self):
        return self._key

    @property
    def _nested_default_value(self):
        return self._key



class VariableOption(OptionBase, NestedDefaultValueMixin, NestedValueProcessorMixin):
    def __init__(self, name, config=None):
        OptionBase.__init__( self, name, config )

        self._var  = None
        self._dest = self._config.get_default_destination( name )
        self._usedefprocs = None

        # This initializes the variable.
        self.default_value( None )

    @property
    def _nested_value_processors(self):
        if self._var is None:
            self._init_variable( None )
        return self._var

    @property
    def _nested_default_value(self):
        if self._var is None:
            self._init_variable( None )
        return self._var

    def _init_variable(self, varname):
        self._var = self._config.create_variable( varname if varname is not None else self.get_name() )

        self._add_expression( self._var )

        if self._dest is not None:
            self._var.destination( self._dest )
        if self._usedefprocs is not None:
            self._var.use_default_processors( self._usedefprocs )

    def destination(self, value):
        self._dest = value if self._var is None else self._var.destination( value )
        return self

    def get_destination(self):
        return self._dest if self._var is None else self._var.get_destination()

    def use_default_processors(self, value):
        self._usedefprocs = value if self._var is None else self._var.use_default_processors( value )
        return self

    def get_use_default_processors(self):
        return self._usedefprocs if self._var is None else self._var.get_use_default_processors()

#    def start_variable(self, varname=None):
#        assert self._var is None
#
#        self._init_variable( varname )
#
#        return self.configure_variable()

    def configure_variable(self):
        return self._var.caller( self )

    def _apply_default_usage_for_subexprs(self, exprfmt, forparamdoc):
        self._key.apply_usage( exprfmt, forparamdoc )

        if self._var:
            self._var.apply_usage( exprfmt, forparamdoc )
        else:
            exprfmt.variable( self.get_name() )



class Option(OptionBase, GroupMixin, SubgroupsMixin):
    def __init__(self, name, config=None):
        OptionBase.__init__( self, name, config )



class SwitchFlag(OptionKeyBase, CommandElement):
    def __init__(self, parent, name, config=None):
        OptionKeyBase.__init__( self, name, config )
        CommandElement.__init__( self )

        self._parent = parent

        Terminal.as_unbound( self ).any_number()

    def execute_actions(self, cntxt, expr, value):
        self._parser.actions( cntxt, expr, value )

    def _apply_default_usage(self, exprfmt, forparamdoc):
        if forparamdoc:
            exprfmt = exprfmt.start_choice()

            exprfmt.constant( self.get_decorated_name() )
            for a in self.get_decorated_aliases():
                exprfmt.constant( a )

            exprfmt = exprfmt.end()
        else:
            exprfmt.cardinality( self.get_min_count(), self.get_max_count() ).constant( self.get_decorated_name() )


class Switch(ParameterMixin, NonterminalElement, SurrogateExpression, DefaultValueMixin):
    def __init__(self, name, config=None):
        SurrogateExpression.__init__( self, config or self.default_grammar )
        ParameterMixin.__init__( self )
        DefaultValueMixin.__init__( self )
        NonterminalElement.__init__( self )

        self._name = name
        self._on   = None
        self._off  = None
        self._cmplower = False

        self.destination( self._config.get_default_destination( name ) ).default_value( False )

    def get_name(self):
        return self._name

    def _create_flag(self, name):
        return SwitchFlag( self, name, self._config )

    def _init_flag(self, name, aliases, value):
        flag = self._create_flag( name ).value( value ).extend_aliases( aliases )

        flag.destination( self.get_destination() ).compare_lowercase( self.get_compare_lowercase() ) \
                .override_value( True )

        self._register_expression( flag )

        return flag

    @property
    def _subexprs(self):
        return [x for x in (self._on, self._off) if x is not None]

    @property
    def _all_subexprs(self):
        return self._subexprs

    def _start_group(self, exprfmt):
        return exprfmt.start_sequence()

    @property
    def _default_value_joint(self):
        return self._scope.get_default_value_joint()

    def compare_lowercase(self, value):
        self._cmplower = value
        for sub in self._subexprs:
            sub.compare_lowercase( value )
        return self

    def get_compare_lowercase(self):
        return self._cmplower

    def destination(self, dest):
        super().destination( dest )
        for sub in self._subexprs:
            sub.destination( dest )
        return self

    def start_on(self, name, *aliases):
        self._on = self._init_flag( name, aliases, True )
        return self.configure_on()

    def on(self, name, *aliases):
        return self.start_on( name, *aliases ).end()

    def configure_on(self):
        return self._on.caller( self )

    def get_on(self):
        return self._on

    def start_off(self, name, *aliases):
        self._off = self._init_flag( name, aliases, False )
        return self.configure_off()

    def off(self, name, *aliases):
        return self.start_off( name, *aliases ).end()

    def configure_off(self):
        return self._off.caller( self )

    def get_off(self):
        return self._off

    def get_min_count(self):
        return 1

    def get_max_count(self):
        return 1

    def _apply_default_usage(self, exprfmt, forparamdoc):
        if forparamdoc:
            exprfmt = exprfmt.start_group( ' and ', False )
            self._on.apply_usage( exprfmt, forparamdoc )
            self._off.apply_usage( exprfmt, forparamdoc )
            exprfmt.end()
        else:
            self._on.apply_usage( exprfmt, forparamdoc )
            self._off.apply_usage( exprfmt, forparamdoc )


# TODO: We could introduce a prefixed expression or maybe more general something like expression modifiers
#       (prefixes, e.g. '-<var>', postfixes, e.g. '<var>!', arbitrary modifications to (current) tokens).
#       Or even more general: compound tokens! A prefix is nothings else than a constant followed by another
#       expression. An arbitrary modifier could be an arbitrary sequence of expressions constituting a single
#       token, for example, '-<var>:<var>,<var>'. Something like expressions in another (sub-)dimension or
#       of a (sub-)order, that is, in a subordinated grammar...
class FlagGroup(NonterminalElement, NonterminalMixin, Terminal):
    def __init__(self, prefix, config=None):
        if config is None:
            config = self.default_grammar

        Terminal.__init__( self, self._create_parser( prefix, config ), config )
        NonterminalMixin.__init__( self )
        NonterminalElement.__init__( self )

        self._flags      = {}
        self._switches   = {}

        self.as_unbound().any_number()

    def _create_parser(self, prefix, config):
        return config.create_flaggroup_parser( self, prefix )

    def get_prefix(self):
        return self._parser.value

    def _add_flag(self, flag):
        for n in chain( (flag.get_name(),), flag.get_aliases()):
            if len( n )==1:
                self._parser.flags[ n ] = flag

    def flag(self, name, *aliases, desc=None):
        flag = self._start_flag( name ).extend_aliases( aliases ).description( desc )

        self._add_flag( flag )
        self._add_expression( flag )

        return self

    def _start_flag(self, name):
        assert name not in self._flags and name not in self._switches

        flag = self._config.create_flag( name )
        self._flags[ name ] = flag
        return flag.caller( self )

    def switch(self, on, off, aliases_on=(), aliases_off=(), desc=None, default_value=Value.Undefined, dest=Value.Undefined):
        sw = self._start_switch( on ).on( on, *aliases_on ).off( off, *aliases_off ).description( desc )

        if default_value!=Value.Undefined:
            sw.default_value( default_value )

        if dest!=Value.Undefined:
            sw.destination( dest )

        self._add_flag( sw.get_on() )
        self._add_flag( sw.get_off() )
        self._add_expression( sw )

        return self

    def _start_switch(self, name):
        assert name not in self._flags and name not in self._switches

        switch = self._config.create_switch( name )
        self._switches[ name ] = switch
        return switch.caller( self )

    # When flags and switches were configurable, their aliases could be changed and they could be configured
    # as bound expressions.
#    def configure(self, name ):
#        return ( self._flags.get( name ) or self._switch.get( name ) ).caller( self )

    def _start_group(self, exprfmt):
        return exprfmt.start_sequence()

    def _apply_default_usage(self, exprfmt, forparamdoc):
        if not forparamdoc:
            exprfmt.optional()
            self._format_value( exprfmt )
        else:
            for sub in self._all_subexprs:
                sub.apply_usage( exprfmt, forparamdoc )

    def apply_doc(self, cmddoc):
        for sub in self._all_subexprs:
            sub.apply_doc( cmddoc )

    def _format_value(self, exprfmt):
        exprfmt.start_item().string( self._parser.prefix ).string( *sorted( self._parser.flags.keys() ) ).end()
