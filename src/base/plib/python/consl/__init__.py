#!/usr/bin/env python3.4

import sys

from consl.cmds import CommandGrammarFamily, CommandExpression, CommandElement
from intrpt import SplitLexer, IntrptContext, Intrpter, ExprError, EvalError
from intrpt.exprfuncs import NestedValueActionMixin, NestedValueProcessorMixin, CompareLowercaseMixin
from intrpt.exprs import SurrogateExpression
from intrpt.valacts import ContextCall, StoreList
from plib.collections import AttributeDict


class Main(CommandExpression):
    def __init__(self, progname, config=None):
        super().__init__( progname, config )

        self._init_cmd()

    def _init_cmd(self):
        self.prepend_name_element( False ) \
            .start_variable( 'progname' ).usage( self.get_name() ).standard_action( None ).end()


class ShowHelp(CommandExpression):
    def __init__(self, consl, name='help', option_aliases=('h', 'help'), config=None):
        super().__init__( name, config )

        self.consl = consl

        self._init_cmd( option_aliases )

    def _init_cmd(self, option_aliases):
        self.extend_aliases( [self._config.get_option_key( a ) for a in option_aliases] ) \
            .abstract( 'Print the help for a given command' ) \
            .description( "The given command can also be a subcommand. In that case, multiple parts "
                          "are passed as command argument, e.g. 'show files', 'show groups', etc." ) \
            .call( self.consl.show_help ) \
            .start_variable( 'command' ) \
                .any_number() \
                .standard_action( StoreList ) \
                .default_value( () ) \
                .start_usage().start_variable( 'command' ).optional().end().end() \
                .end()


class HelpOption(CommandElement, SurrogateExpression, NestedValueActionMixin, NestedValueProcessorMixin,
                 CompareLowercaseMixin):
    def __init__(self, consl, name='help', config=None):
        CommandElement.__init__( self )
        SurrogateExpression.__init__( self, config or self.default_grammar )

        self.consl = consl

        self._flagval = self._config.get_option_key( name )
        self._acts    = self._config.create_value_action_collection()
        self._procs   = self._config.create_value_processor_collection()

        self._acts.store( 'command' ).call( consl.show_help )

    @property
    def _nested_value_actions(self):
        return self._acts

    @property
    def _nested_value_processors(self):
        return self._procs

    @property
    def _cmplower_processor_collection(self):
        return self._procs

    def get_as_unbound(self):
        return False

    def parse(self, cntxt):
        cmdpath = []

        if cntxt.current is None or cntxt.tokens[ -1 ]!=self._flagval:
            cntxt.on_error( self )
        else:
            try:
                curcmd  = self.consl.find_command( () )

                while cntxt.position<len( cntxt.tokens )-1:
                    value = self._parse_value( cntxt, cntxt.current )
                    curcmd = curcmd.find_command( (value,) )
                    cmdpath.append( value )
                    cntxt.forward()

                cntxt.forward()
                self._on_success( cntxt, cmdpath )
            except ValueError as err:
                cntxt.on_value_error( self, suberrs=err )
            except KeyError:
                cntxt.on_error( self )

        return cmdpath

    def _parse_value(self, cntxt, value):
        return self._procs( cntxt, self, value, 0 )

    def _on_success(self, cntxt, value):
        self._acts( cntxt, self, value )

    @property
    def _subexprs(self):
        return ()

    def format(self, exprfmt):
        exprfmt.start_sequence().variable( 'command' ).constant( self._flagval ).end()

    def _apply_default_usage(self, exprfmt, forparamdoc):
        self.format( exprfmt )



# TODO: Define a generic console interface that can be used independently of intrpt (as intrpt can be
#       used independently of expressions...)
class Consl:
    class CommandIntrpter(Intrpter):
        def __init__(self, grammar, fallback_grammar, env, lexer, cntxtclass):
            super().__init__( grammar, env, lexer, cntxtclass )
            self.fallback_grammar = fallback_grammar

        def parse(self, tokens):
            try:
                return super().parse( tokens )
            except ExprError as err:
                try:
                    if self.fallback_grammar:
                        return self._do_parse( tokens, self.fallback_grammar )
                except ExprError:
                    pass

                raise err

    def __init__(self, grammar, fallback_grammar=None, lexer=SplitLexer(), cntxtclass=IntrptContext):
        self.env = AttributeDict()
        self._intrpter = self.CommandIntrpter( grammar, fallback_grammar, self.env, lexer, cntxtclass )

    def _set_grammar(self, grammar, fallback_grammar):
        self._intrpter.grammar = grammar
        self._intrpter.fallback_grammar = fallback_grammar

    def find_command(self, cmdpath):
        return self._intrpter.grammar.find_command( cmdpath )

    def print_doc(self, cmdpath):
        self.find_command( cmdpath ).create_doc( cmdpath ).write()

    def parse(self, tokens):
        return self._intrpter.parse( tokens )

    def evaluate(self, args=None, exit_on_error=True):
        if args==None:
            args = sys.argv

        try:
            return self.parse( args ).evaluate()
        except EvalError as e:
            if not exit_on_error:
                raise e
            else:
                if len( args )==1:
                    self.print_doc( () )
                else:
                    print( e )
                sys.exit( 1 )

    def evaluate_string(self, str_):
        return self._intrpter.evaluate_string( str_ )

    def show_help(self, args):
        self.print_doc( args.command )

    @classmethod
    def _create_main(cls, config, progname):
        return Main( progname, config )

    # TODO: This should be a configurable choice in main. However, who creates the necessary context? Or can it
    #       be some kind of special rule for the given context? The general problem: This command is not
    #       prefix-free but should work without backtracking. (<progname> (cmd ... | cmd --help)
    #       Another problem: <progname> (cmd | cmd --help) -> cmd is successfully parsed and then '--help' is
    #       regarded as excess expression. Solution: Expressions already know when they are root.
    #       Finalising a context should be moved to expressions.
    def _create_help_fallback(self, config, progname):
        return self._create_main( config, progname ).add_expression( HelpOption( self ).compare_lowercase() )

    def _create_help_cmd(self, config, name='help', option_aliases=('h', 'help')):
        return ShowHelp( self, name, option_aliases)
