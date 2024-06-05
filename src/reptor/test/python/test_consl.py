#!/usr/bin/env python3.4

import unittest

from consl import Main, HelpOption, Consl, ShowHelp
from consl.cmds import CommandGrammarFamily, CommandExpression
from intrpt.valacts import ContextCall, StoreList
from intrpt.valprocs import RangeChecker


#@unittest.skip
class ConslDummyTest(unittest.TestCase):
    def test(self):
        cmdbld = Main( 'splitshell' ) \
                    .abstract( 'splitshell abstract' ) \
                    .description( 'splitshell description' )

        def create_help(name='help', config=CommandGrammarFamily):
            def print_help(cntxt):
                cntxt.env.print_doc( cntxt.args_store.command )

            return config.create_command( name ) \
                            .abstract( 'Print the help for a given command' ) \
                            .description( "The given command can also be a subcommand. In that case, multiple parts "
                                          "are passed as command argument, e.g. 'show files', 'show groups', etc." ) \
                            .add_action( ContextCall( print_help ) ) \
                            .start_variable( 'command' ) \
                                .any_number() \
                                .standard_action( StoreList ) \
                                .default_value( () ) \
                                .start_usage().start_variable( 'command' ).optional().end().end() \
                                .end()

#        def create_help_flag_builder(name='help', config=CommandGrammarFamily):
#            def print_help(cntxt):
#                cntxt.env.print_doc( cntxt.args_store.command )
#
#            bld = config.create_option_builder( name ) \
#                            .alias( 'h' ) \
#                            .description( "The given command can also be a subcommand. In that case, multiple parts "
#                                          "are passed as command argument, e.g. 'show files', 'show groups', etc." )
#
#            bld.configure_key() \
#                    .action( ContextCall( print_help ) )
#
#            bld.start_variable( 'command' ) \
#                    .any_number() \
#                    .standard_action( StoreList ) \
#                    .default_value( () )
#
#            bld.start_usage() \
#                    .start_constant( config.get_option_key( name ) ) \
#                        .optional()
#            bld.start_extended_usage() \
#                    .start_choice() \
#                        .constant( config.get_option_key( name ) ) \
#                        .constant( config.get_option_key( 'h' ) ) \
#                        .end() \
#                    .start_variable( 'command' ) \
#                        .optional()
#
#            return bld
#
#
#
#        cmdbld.add_expression( create_help_flag_builder() )

#        def help_flag(cntxt):
#            cntxt.env.print_doc( cntxt.args_store.cmdpath[ :-1:-1 ] )
#
#        cmdbld.start_flag( 'help' ) \
#                .description( 'Prints the help for a given command.' ) \
#                .alias( 'h' ) \
#                .action( ContextCall( help_flag ) ) \
#                .clear_value() \
#                .clear_default_value() \

        def exec_func(args):
            if args.func is not None:
                args.func( args )
                args.func = None
                print( args )

        worldcmd = CommandExpression( 'world' ) \
            .abstract( 'That is the world' ) \
            .configure_name() \
                .store_const( 'func', lambda x: print( 'WORLD' ), True ) \
                .end() \
            .call( exec_func ) \
            .start_command( 'otherworld' ) \
                .optional() \
                .abstract( 'Another world' ) \
                .configure_name() \
                    .store_const( 'func', lambda x: print( 'ANOTHER WORLD' ), True ) \
                    .end() \
                .call( exec_func ) \
                .end() \

        cmdbld.start_subcommands() \
                .optional() \
                .add_expression( create_help( 'help' ) ) \
                .add_expression( worldcmd ) \
                .start_command( 'hello' ) \
                    .abstract( 'And that is a hello' ) \
                    .variable( 'hello_var' ) \
                    .end()

#        cmddoc = cmdbld.create_doc()
#        cmddoc.write()
#
#        cmdidx = cmdbld.create_doc_index()
#        cmdidx.find( ('help',) ).create_doc().write()
        helpgrammar = Main( 'testapp' )

        cli = Consl( cmdbld, helpgrammar )

        helpgrammar.add_expression( HelpOption( cli ).compare_lowercase() )
#        cli.evaluate_string( 'split help world' )
#        cli.evaluate_string( 'split help world' )
#        cli.evaluate_string( 'split help hello' )
#        cli.evaluate_string( 'split world otherworld' )
#        cli.evaluate( ('split', 'help') )
#        cli.evaluate( ('split', 'help', 'help') )
#        cmdbld.evaluate( ('split', 'help', 'help', 'help') )

        cli.evaluate_string( 'testapp world otherworlD --help' )



#@unittest.skip
class SplitshellTest(unittest.TestCase):
    def setUp(self):
        def process_hostgroup(cntxt, expr, value, error_level):
            print( 'get host group for {}'.format( value ) )
            return value

        def process_network(cntxt, expr, value, error_level):
            print( 'get network for {}'.format( value ) )
            return value

        def create_split_host_shell(args):
            print( "exec host group '{}' user '{}' net '{}' wmode '{}' pmode '{}'" \
                   .format( args.hostgroup, args.user, args.network, args.window_mode, args.print_only ) )

        groupcmd = CommandExpression( 'group' ) \
                        .abstract( 'Connect to a host group' ) \
                        .description( 'Creates a split shell to all hosts within a group.' ) \
                        .prepend_name_element( False ) \
                        .call( create_split_host_shell ) \
                        .start_variable( 'hostgroup' ) \
                            .add_processor( process_hostgroup ) \
                            .end()

        groupcmd.variable_option( 'user', 'u', desc='Login used for SSH connection.' )

        groupcmd.start_variable_option( 'network', 'n' ) \
                .description( 'Network if hosts have multiple NICs.' ) \
                .add_processor( process_network )

        groupcmd.start_flaggroup() \
                .flag( 'print-only', 'p', desc='Not used.' ) \
                .flag( 'window-mode', 'w', desc='Not sure about this one.' )

        mainbld = Main( 'splitshell' )

        self.cli = Consl( mainbld )

        mainbld.start_subcommands() \
            .add_expression( ShowHelp( self.cli ) ) \
            .add_expression( groupcmd ) \
            .end()

#   def test_command(self):
#       self.cli.evaluate_string( 'splitshell beagles -pw -n net1 -u user1' )

    def test_help(self):
#       self.cli.evaluate_string( 'splitshell help' )
#      print()
        self.cli.evaluate_string( 'splitshell -h' )



#@unittest.skip
class MonitorTest(unittest.TestCase):
    def setUp(self):
        mainbld = Main( 'monitor' )

        mainbld.start_variable_option( 'interval', 'i' ) \
                .description( 'Sample interval in fractions of seconds.' ) \
                .convert_float() \
                .add_processor( RangeChecker( 0.0, inclmin=False ) ) \
                .use_default_processors( False ) \
                .default_value( 1.0 )

        mainbld.start_option( 'max-intervals', 'm' ) \
                .description( 'The maximum number of intervals to be monitored.' ) \
                .start_choice() \
                    .start_variable( 'max-intervals' ) \
                        .default_value( None ) \
                        .convert_int() \
                        .add_processor( RangeChecker( 1 ) ) \
                        .use_default_processors( False ) \
                        .end() \
                    .constant( 'none' )

        mainbld.start_variable_option( 'result-dir', 'd' ) \
                .description( 'Path at which the results shall be saved.' ) \
                .default_value( '.' )

        mainbld.start_variable_option( 'file-pattern', 'f' ) \
                .description( 'Pattern for generated files. {rectype} and {recname} are replaced by '
                              'the type and the name of the resource monitor, respectively.' ) \
                .default_value( 'resources_{rectype}_{recname}.log' )

        mainbld.variable_option( 'monitor-host', 'h', desc='Host that shall be monitored.' )

        mainbld.start_flaggroup() \
                .switch( 'percpu', 'not-percpu', ('c',), ('C',), desc='Monitor load per core.' ) \
                .switch( 'pernic', 'not-pernic', ('n',), ('N',), desc='Monitor rates per NIC.' ) \
                .switch( 'perthread', 'not-perthread', ('t',), ('T',), desc='Monitor load per thread.' ) \
                .switch( 'monitor-procs', 'do-not-monitor-procs', ('p',), ('P',), desc='Monitor processes at the host.', default_value=True )

        def final_check(cntxt, expr):
            args = cntxt.args_store
            if not args.monitor_procs and not args.monitor_host:
                cntxt.on_error( expr, msg='At least processes or the host have to be monitored' )

        mainbld.configure_scope().configure_finalize_events().add_action( final_check )
        self.cli = Consl( mainbld )

    def test_command(self):
        cntxt = self.cli.evaluate_string( 'monitor -i 2.2 -m 2' )
        print( cntxt.args_store )

    def test_help(self):
        self.cli.print_doc( () )


if __name__=="__main__":
    unittest.main()
