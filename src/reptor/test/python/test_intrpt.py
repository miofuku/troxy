#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from itertools import permutations
from random import shuffle
import unittest

from intrpt.evtacts import Default
from intrpt.exprs import Constant
from intrpt.grammars import StandardGrammarFamily
from intrpt import Intrpter, ExprError, ExprValueError, EvalError
from intrpt.parsers import ConstantParser
from intrpt.valacts import Count, Evaluation, StoreList
from intrpt.valprocs import StartsWithChecker


@unittest.skip
class Test(unittest.TestCase):
    def test(self):
#        root = StandardGrammarFamily.create_sequence( creates_scope=True )
#
#        root.variable( 'hello' )#.variable( 'world' )
#
#        var0 = root._subexprs[ 0 ]
#
#        root.add_expression( var0 )
#
#        print( root._subexprs )
##        print( root._subexprs[ 2 ]._scopes )
#
#        print( var0.get_has_default_value() )
#        print( var0._finalizing_joint._actions )
#        var0.default_value( None )
#        print( var0.get_has_default_value() )
#        print( var0._finalizing_joint._actions )
#        var0.destination( 'test' )
#        print( var0.get_has_default_value() )
#        print( var0._finalizing_joint._actions )
#        var0.clear_default_value()
#        print( var0._finalizing_joint._actions )
#        print( var0.get_has_default_value() )
#        var0.default_value( None )
#        print( var0._finalizing_joint._actions )
#        print( var0.get_has_default_value() )

        root = StandardGrammarFamily.create_sequence()
        var0 = root.start_variable( 'a' )

        root.configure_scope().create_new_scope()
        root.configure_scope().create_new_scope( False )
        root.configure_scope().create_new_scope()

        d = Default( '0', 0 )
        var0.configure_scope().configure_leave_events().add_action( d ).remove_action( d ).add_action( d ).add_action( d )


        seq1 = root.start_sequence()
        seq1.variable( 'b' )

        root.configure_scope().configure_enter_events().default_value( 'r', 0 ).end().configure_finalize_events().default_value( 'r', 1 )

        var0.configure_scope().create_new_scope( True ).configure_finalize_events().add_action( d ).end().create_new_scope( False )

        var2 = StandardGrammarFamily.create_variable( 'c' )
        var2.configure_scope().configure_enter_events().default_value( 'c', 0 )
        seq1.add_expression( var2 )

        root2 = StandardGrammarFamily.create_sequence()
        root2.configure_scope().create_new_scope()
        root2.add_expression( seq1 )

        seq1.start_variable( 'd' ).configure_scope().configure_leave_events().default_value( 'd', 0 )

        seq1.configure_scope().create_new_scope( False )

        roots = StandardGrammarFamily.create_sequence().configure_scope().create_new_scope().end()
        roots.add_expression( root )
        roots.add_expression( root2 )

        root.configure_scope().create_new_scope( False )
        root2.configure_scope().create_new_scope( False )
        root.configure_scope().create_new_scope( True )
        root2.configure_scope().create_new_scope( True )

        self.print_scope( root )
        self.print_scope( var0 )
        self.print_scope( var2 )
        self.print_scope( seq1 )
        self.print_scope( root2 )
        self.print_scope( roots )

    def print_scope(self, expr):
        print( 'exp ' + str(expr) )
        print( ' s  ' + str(expr._parser.scope._outer_scopes) )
        print( ' 0  ' + str(expr._parser.scope._joints[ 0 ]._actions) )
        print( ' 1  ' + str(expr._parser.scope._joints[ 1 ]._actions) )
        print( ' 2  ' + str(expr._parser.scope._joints[ 2 ]._actions) )



backtracking_default = False

class TerminalTests:
    class Base(unittest.TestCase, metaclass=ABCMeta):
        def setUp(self):
            self._intr = Intrpter( self._create_expr( StandardGrammarFamily ), backtracking_default=backtracking_default )

        def test_matching_too_many(self):
            m = self._matching_tokens
            self._test_error( m + ['too', 'many'], len( m ), None )

        def test_not_matching(self):
            self._test_error( self._not_matchting_tokens, 0, self._intr.grammar )

        def test_not_matching_too_many(self):
            self._test_error( self._not_matchting_tokens + ['too', 'many'], 0, self._intr.grammar )

        def test_too_few(self):
            self._test_error( [], 0, self._intr.grammar )

        def _test_error(self, tokens, pos, errexpr):
            with self.assertRaises( ExprError ) as cm:
                self._intr.parse( tokens ).evaluate()
            self.assertEqual( cm.exception.pos, pos )
            self.assertEqual( cm.exception.expr, errexpr )

        @abstractmethod
        def _create_expr(self, config):
            ...

        @property
        @abstractmethod
        def _matching_tokens(self):
            ...

        @property
        def _not_matchting_tokens(self):
            return ['nope']


#@unittest.skip
class ConstantTest(TerminalTests.Base):
    class SpecialConstant(Constant):
        class Parser(ConstantParser):
            def _on_success(self, cntxt, value):
                cntxt.register_evaluation( self )
                super()._on_success( cntxt, value )

            def evaluate(self, cntxt):
                return True

        def _create_parser(self, value, config):
            return self.Parser( self, value, config )

    def _create_expr(self, config):
        return ConstantTest.SpecialConstant( 'test', config )

    @property
    def _matching_tokens(self):
        return ['test']

    def test_matching(self):
        res = self._intr.parse( self._matching_tokens ).get_result()
        self.assertEqual( res, True )

#@unittest.skip
class VariableTest(TerminalTests.Base):
    def _create_expr(self, config):
        return config.create_variable( 'test' ).convert_int()

    @property
    def _matching_tokens(self):
        return ['3']

    def test_matching(self):
        res = self._intr.parse( self._matching_tokens ).evaluate()
        self.assertEqual( res.args_store.test, 3 )


#@unittest.skip
class SequenceTest(unittest.TestCase):
    def setUp(self):
#        cy = Constant( 'year', StandardGrammarFamily )
#        vy = Variable( 'year', StandardGrammarFamily )._parser.processors._procs.append( Converter.Int )
#        cm = Constant( 'month', StandardGrammarFamily )
#        vm = Variable( 'month', StandardGrammarFamily )._parser.processors._procs.append( Converter.Int )
#        cd = Constant( 'day', StandardGrammarFamily )
#        vd = Variable( 'day', StandardGrammarFamily )._parser.processors._procs.append( Converter.Int )
#        grammar = Sequence( StandardGrammarFamily )
#        grammar._parser.subprocs = (cy, vy, cm, vm, cd, vd)


        grammar = StandardGrammarFamily.create_sequence() \
                        .constant( 'year' ).start_variable( 'year' ).convert_int().end() \
                        .constant( 'month' ).start_variable( 'month' ).convert_int().end() \
                        .constant( 'day' ).start_variable( 'day' ).convert_int().end()

        self._intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_matching(self):
        res = self._intr.evaluate_string( 'year 2015 month 2 day 7' )
        self.assertEqual( res.args_store.day, 7 )


#@unittest.skip
class ChoiceTest(unittest.TestCase):
    def setUp(self):
        grammar = StandardGrammarFamily.create_choice()
        grammar.start_sequence().constant( 'year' ).start_variable( 'year' ).convert_int()
        grammar.start_sequence().constant( 'month' ).start_variable( 'month' ).convert_int()
        grammar.start_sequence().constant( 'day' ).start_variable( 'day' ).convert_int()

        self._intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_matching_year(self):
        res = self._intr.evaluate_string( 'year 2015' )
        self.assertEqual( res.args_store.year, 2015 )

    def test_matching_month(self):
        res = self._intr.evaluate_string( 'month 2' )
        self.assertEqual( res.args_store.month, 2 )

    def test_matching_day(self):
        res = self._intr.evaluate_string( 'day 7' )
        self.assertEqual( res.args_store.day, 7 )

    def test_not_matching(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'month apr' )
        self.assertEqual( cm.exception.pos, 1 )

    def test_empty(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( '' )
        self.assertEqual( cm.exception.pos, 0 )

#@unittest.skip
class RepetitionTest(unittest.TestCase):
    def setUp(self):
        grammar = StandardGrammarFamily.create_sequence()

        grammar.start_sequence().constant( 'year' ).start_variable( 'year' ).convert_int()

        co = grammar.start_choice().cardinality( 0, 2 )
        co.start_sequence().cardinality( 1, 2 ).constant( 'month' ).start_variable( 'month' ).convert_int()
        co.start_sequence().constant( 'day' ).start_variable( 'day' ).convert_int().standard_action( None ).store( 'day', True )

        self._intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_matching_month(self):
        res = self._intr.evaluate_string( 'year 2015 month 2' )
        self.assertEqual( res.args_store.month, 2 )

    def test_matching_day(self):
        res = self._intr.evaluate_string( 'year 2015 day 7' )
        self.assertEqual( res.args_store.day, 7 )

    def test_not_matching_day(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 day 7 day mon' )
        self.assertEqual( cm.exception.pos, 5 )

    def test_not_matching_month(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 month 2 month feb' )
        self.assertEqual( cm.exception.pos, 5 )


    def test_matching_days(self):
        res = self._intr.evaluate_string( 'year 2015 day 7 day 8' )
        self.assertEqual( res.args_store.day, 8 )

    def test_empty_choice(self):
        res = self._intr.evaluate_string( 'year 2015' )
        self.assertEqual( res.args_store.year, 2015 )


#@unittest.skip
class UnboundTest(unittest.TestCase):
    def setUp(self):
        grammar = StandardGrammarFamily.create_sequence().create_new_scope()

        grammar.start_sequence().create_new_scope().constant( 'year' ).start_variable( 'year' ).convert_int()

        co = grammar.start_choice()
        co.start_sequence().constant( 'month' ).start_variable( 'month' ).convert_int()
        co.start_sequence().create_new_scope().constant( 'day' ).start_variable( 'day' ).convert_int()

        grammar.start_sequence().as_unbound().cardinality( 0, 1 ).constant( 'add' ).start_variable( 'add' ).convert_int()
        grammar.start_constant( '-v' ).as_unbound().cardinality( 1, 3 )

        self._intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_matching_no_scope(self):
        res = self._intr.evaluate_string( 'year 2015 month -v 2 -v' )
        self.assertEqual( res.args_store.month, 2 )

    def test_matching_last(self):
        res = self._intr.evaluate_string( 'year 2015 day 7 -v' )
        self.assertEqual( res.args_store.day, 7 )

    def test_matching_middle(self):
        res = self._intr.evaluate_string( 'year 2015 -v day 7' )
        self.assertEqual( res.args_store.day, 7 )

    def test_not_allowed(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 day -v 7' )
        self.assertEqual( cm.exception.pos, 3 )

    def test_too_few(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 day 7' )
        self.assertEqual( cm.exception.pos, 4 )

    def test_too_many(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 -v -v -v day 7 -v' )
        self.assertEqual( cm.exception.pos, 7 )

#@unittest.skip
class UnboundTest2(unittest.TestCase):
    def check(self, cntxt, expr):
        store = cntxt.args_store
        if 'user' in store and 'network' in store and (store.user!='1' and store.user!='test' or store.network!='2'):
            cntxt.on_error( expr, msg='User and network do not fit.' )

    def setUp(self):
        grammar = StandardGrammarFamily.create_sequence().variable( 'prog' )

        cmd = grammar.start_sequence().create_new_scope() \
                        .start_variable( 'hostgroup' ) \
                            .add_processor( StartsWithChecker( '--', True ) ) \
                            .end()

        cmd.start_sequence() \
                    .as_unbound().optional() \
                    .constant( '--user', '-u' ) \
                    .start_variable( 'user' ).default_value( 'test' ).end()

        cmd.start_sequence() \
                    .as_unbound().optional() \
                    .constant( '--network', '-n' ) \
                    .variable( 'network' )

        grammar.configure_events().configure_after_parsing().add_action( self.check )

        self._intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_missing(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'testit clouds --user' )
        self.assertEqual( cm.exception.pos, 3, cm.exception )

    def test_wrong_option_pos(self):
        with self.assertRaises( ExprValueError ) as cm:
            self._intr.evaluate_string( 'testit --network 4 clouds' )
        self.assertEqual( cm.exception.pos, 1, cm.exception )

    def test_matching_all(self):
        res = self._intr.evaluate_string( 'testit clouds -u 1 -n 2' )
        self.assertEqual( res.args_store.user, '1' )
        self.assertEqual( res.args_store.network, '2' )

    def test_matching_all2(self):
        res = self._intr.evaluate_string( 'testit clouds -n 2 -u 1' )
        self.assertEqual( res.args_store.user, '1' )
        self.assertEqual( res.args_store.network, '2' )

    def test_default(self):
        res = self._intr.evaluate_string( 'testit clouds -n 2' )
        self.assertEqual( res.args_store.user, 'test' )
        self.assertEqual( res.args_store.network, '2' )

    def test_global_checker(self):
        with self.assertRaises( EvalError ):
            self._intr.evaluate_string( 'testit clouds -n 3' )

#@unittest.skip
class FloatingTest(unittest.TestCase):
    def _create_floating(self, name, consts):
        act = Count( name )
        seq = StandardGrammarFamily.create_sequence()

        for c in consts:
            expr = seq.start_constant( c ).as_unbound().register_unbound_at_scope( False ).add_action( act )
            ubc  = expr.get_unbound_control()
            ubc.activate_on( seq.configure_events().before_parsing )
            ubc.finish_on( seq.configure_events().after_parsing )

        return seq

    def setUp(self):
        self.hello  = 'hello world !!!'.split()
        self.twenty = 'twenty one'.split()
        self.half   = 'half of the truth'.split()

        c0 = self._create_floating( 'twenty', self.twenty )
        c1 = self._create_floating( 'hello', self.hello )
        c2 = self._create_floating( 'half', self.half )

        grammar = StandardGrammarFamily.create_choice().extend_expressions( (c0, c1, c2) )

        self.intr = Intrpter( grammar, backtracking_default=backtracking_default )

    def test_as_you_prefer(self):
        for v in permutations( self.hello ):
            with self.subTest( str( v ) ):
                res = self.intr.parse( v ).evaluate()
                self.assertEqual( res.args_store.hello, len( self.hello ) )

    def test_wrong_unbound(self):
        with self.assertRaises( ExprError ) as cm:
            self.intr.evaluate_string( 'half hello of the' )
        self.assertEqual( cm.exception.pos, 1 )

    def test_too_many(self):
        with self.assertRaises( ExprError ) as cm:
            self.intr.evaluate_string( 'twenty one half' )
        self.assertEqual( cm.exception.pos, 2 )


## The and ... is to suppress warnings about unused types
#@unittest.skip
class PrefixTest(unittest.TestCase):
    class TestEval(Evaluation):
        def evaluate(self, cntxt):
            cntxt.args_store.eval = True

    def setUp(self):
#        cy = Constant( 'year' )
#        vy = Variable( 'year', Converter.Int,
#                action=ActionList( (Const( Store( 'happy' ), 'happy' ), Store( 'year' )) ) )
#        sy = Sequence( (cy, vy), creates_scope=True )
#
#        cm = Constant( 'month', action=Const( Store( 'override', True ), 1 ) )
#        vm = Variable( 'month', Converter.Int, action=StoreList( 'month' ) )
#        cd = Constant( 'day', action=ActionList( (self.TestEval(), Const( Store( 'override', True ), 2 )) ) )
#        vd = Variable( 'day', Converter.Int, action=Store( 'day', True ) )
#        cf = Constant( 'stop' )
#        rf = Repetition( cf )
#        c2 = Constant( 'day' )
#
#        sd = Sequence( (cm, vm, rf, cd, vd) )
#        sm = Sequence( (cm, vm, rf, c2) )
#        co = Repetition( Choice( (sd, sm), use_indexer=False ), 1, 3 )
#
#        mainseq = Sequence( (sy, co) )
#
#        cv = Constant( '-v', action=Count( 'v' ) )
#        ov = Unbound( cv, 1, 4 )
#        ov.activate_on( sy.after_finishing )
#        ov.finish_on( mainseq.after_parsing )
#        ov.finish_on( cf.after_parsing )
#
#        cu = Constant( '-u', action=Count( 'u' ) )
#        ou = Unbound( cu, 0, 3 )
#        ou.activate_on( sy.after_finishing )
#        ou.finish_on( mainseq.after_parsing )
#        ou.finish_on( cf.after_parsing )
#
#        fg = FlagGroup( '-' )
#        fg.flags[ 'v' ] = FlagGroup.Flag( ov, cv.action )
#        fg.flags[ 'u' ] = FlagGroup.Flag( ou, cu.action )
#        of = Unbound( fg, 0, None, creates_scope=False )
#        of.activate_on( sy.after_finishing )
#        of.finish_on( mainseq.after_parsing )
#        of.finish_on( cf.after_parsing )
#
#        grammar = mainseq

        cy   = StandardGrammarFamily.create_constant( 'year' )
        vy   = StandardGrammarFamily.create_variable( 'year' ).convert_int().store_const( 'happy', 'happy' )
        stop = StandardGrammarFamily.create_constant( 'stop' ).optional()
        cm   = StandardGrammarFamily.create_constant( 'month' ).store_const( 'override', 1, True )
        vm   = StandardGrammarFamily.create_variable( 'month' ).convert_int().standard_action( None ).add_action( StoreList( 'month' ) )
        cd   = StandardGrammarFamily.create_constant( 'day' ).store_const( 'override', 2, True ).add_action( self.TestEval() )
        vd   = StandardGrammarFamily.create_variable( 'day' ).convert_int().standard_action( None ).store( 'day', True )

        grammar = StandardGrammarFamily.create_sequence().create_new_scope()

        sy = grammar.start_sequence().create_new_scope()
        sy.extend_expressions( (cy, vy) )
        co = grammar.start_choice().cardinality( 1, 3 ).use_indexer( False )
        co.start_sequence().extend_expressions( (cm, vm, stop, cd, vd) )
        co.start_sequence().extend_expressions( (cm, vm, stop) ).constant( 'day' )

        cv = StandardGrammarFamily.create_constant( '-v' ).add_action( Count( 'v' ) ).as_unbound().register_unbound_at_scope( False ).cardinality( 1, 4 )
        ov = cv.get_unbound_control()
        ov.activate_on( sy.get_events().after_finishing )
        ov.finish_on( grammar.get_events().after_parsing )
        ov.finish_on( stop.get_events().after_parsing )

        cu = StandardGrammarFamily.create_constant( '-u' ).add_action( Count( 'u' ) ).as_unbound().register_unbound_at_scope( False ).cardinality( 0, 3 )
        ou = cu.get_unbound_control()
        ou.activate_on( sy.get_events().after_finishing )
        ou.finish_on( grammar.get_events().after_parsing )
        ou.finish_on( stop.get_events().after_parsing )

        # TODO: insert
#        fg = FlagGroup( '-' )
#        fg.flags[ 'v' ] = FlagGroup.Flag( ov, cv.action )
#        fg.flags[ 'u' ] = FlagGroup.Flag( ou, cu.action )
#        of = Unbound( fg, 0, None, creates_scope=False )
#        of.activate_on( sy.after_finishing )
#        of.finish_on( mainseq.after_parsing )
#        of.finish_on( cf.after_parsing )


        self._intr = Intrpter( grammar, backtracking_default=True )

#    def test_matching_month_simple(self):
#        res = self._intr.evaluate_string( 'year 2015 -v -uvu month 7 day' )
#        self.assertEqual( res.args_store.year, 2015 )
#        self.assertEqual( res.args_store.happy, 'happy' )
#        self.assertEqual( res.args_store.v, 2 )
#        self.assertEqual( res.args_store.u, 2 )
#        self.assertEqual( res.args_store.override, 1 )
#        self.assertNotIn( 'eval', res.args_store )
#
#    def test_not_matching_flag(self):
#        with self.assertRaises( ExprError ) as cm:
#            self._intr.evaluate_string( 'year 2015 -v -uvf month 7 day' )
#        self.assertEqual( cm.exception.pos, 3 )
#
#    def test_matching_month(self):
#        res = self._intr.evaluate_string( 'year 2015 -v -v month 7 -v day -vu' )
#        self.assertEqual( res.args_store.v, 4 )
#        self.assertEqual( res.args_store.u, 1 )
#        self.assertEqual( res.args_store.year, 2015 )
#        self.assertEqual( res.args_store.happy, 'happy' )
#        self.assertNotIn( 'eval', res.args_store )
#
    def test_matching_day(self):
        res = self._intr.evaluate_string( 'year 2015 -v month 2 -v day 7 -v -v' )
        self.assertEqual( res.args_store.v, 4 )
        self.assertTrue( res.args_store.eval )
        self.assertEqual( res.args_store.override, 2 )

    def test_not_matching_stop(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 -v month 2 -v stop day 7 -v -v' )
        self.assertEqual( cm.exception.pos, 9 )

    def test_matching_single_day(self):
        res = self._intr.evaluate_string( 'year 2015 -v month 2 -v day -v' )
        self.assertEqual( res.args_store.v, 3 )
        self.assertNotIn( 'eval', res.args_store )

    def test_matching_stop_single_day(self):
        res = self._intr.evaluate_string( 'year 2015 -v month 2 -v stop day' )
        self.assertEqual( res.args_store.v, 2 )
        self.assertNotIn( 'eval', res.args_store )

    def test_not_matching_stop_single_day(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( 'year 2015 -v month 2 -v stop day -v' )
        self.assertEqual( cm.exception.pos, 8 )

    def test_list_months(self):
        res = self._intr.evaluate_string( 'year 2015 -v month 1 -v day 3 month 4 day 2 month 2 -v day -v' )
        self.assertEqual( res.args_store.month, [1, 4, 2] )
        self.assertTrue( res.args_store.eval )


#@unittest.skip
class OptionalBTTest(unittest.TestCase):
    def setUp(self):
        grammar = StandardGrammarFamily.create_choice()

        grammar.start_choice() \
                    .constant( 'hello' ) \
                    .constant( 'world' )
        grammar.start_choice() \
                    .backtracking( True ) \
                    .start_sequence().variable( 'a' ).variable( 'b' ).variable( 'c' ).end() \
                    .start_sequence().variable( 'total' )


        self._intr = Intrpter( grammar, backtracking_default=False )

    def test_total(self):
        res = self._intr.evaluate_string( '!!!' )

        self.assertEqual( res.args_store.total, '!!!' )
        self.assertEqual( len( res.args_store ), 1 )

    def test_two(self):
        with self.assertRaises( ExprError ) as cm:
            self._intr.evaluate_string( '! !' )

        self.assertEqual( cm.exception.pos, 2 )

    def test_parts(self):
        res = self._intr.evaluate_string( '! ! !' )

        self.assertEqual( res.args_store.a, '!' )
        self.assertEqual( res.args_store.b, '!' )
        self.assertEqual( res.args_store.c, '!' )
        self.assertEqual( len( res.args_store ), 3 )

#@unittest.skip
class KeyValueTest(unittest.TestCase):
    def _create_floating(self, name, consts):
        act = Count( name )
        seq = StandardGrammarFamily.create_sequence()

        for c in consts:
            expr = seq.start_constant( c ).as_unbound().register_unbound_at_scope( False ).add_action( act )
            ubc  = expr.get_unbound_control()
            ubc.activate_on( seq.configure_events().before_parsing )
            ubc.finish_on( seq.configure_events().after_parsing )

        return seq

    def setUp(self):
        main = StandardGrammarFamily.create_sequence()

        self.keys = []

        for i in range( 50 ):
            name  = 'key_{}'.format( i )

            self.keys.append( name )

            entry = main.start_sequence().as_unbound().register_unbound_at_scope( False ).constant( name ).start_variable( name ).convert_int().end()
            entry.get_unbound_control().activate_on( main.get_events().before_parsing )
            entry.get_unbound_control().finish_on( main.get_events().after_parsing )

        self.intr = Intrpter( main, backtracking_default=backtracking_default )

    def test(self):
        shuffle( self.keys )

        tokens = [ l for i, k in enumerate( self.keys ) for l in (k, i) ]

        self.intr.parse( tokens ).evaluate()

#@unittest.skip
class IndexerTest(unittest.TestCase):
    def test(self):
        c0 = StandardGrammarFamily.create_choice()

        c01 = c0.start_choice() \
                .start_sequence().constant( 'a' ).constant( 'a2' ).end() \
                .start_sequence().constant( 'b' ).end() \
                .start_sequence().constant( 'c' ).end()

        self.assertIndices( c0, ('a', 'b', 'c'), [] )
        self.assertIndices( c01, ('a', 'b', 'c'), [] )

        c02 = c0.start_choice() \
                .start_sequence().constant( 'd' ).end() \
                .start_sequence().constant( 'e' ).end()
        s02 = c02.start_sequence()

        self.assertIndices( c0, ('a', 'b', 'c', 'd', 'e'), [] )
        self.assertIndices( c01, ('a', 'b', 'c'), [] )
        self.assertIndices( c02, ('d', 'e'), [] )

        s02.variable( 'f' )

        self.assertIndices( c0, ('a', 'b', 'c', 'd', 'e'), [c02] )
        self.assertIndices( c01, ('a', 'b', 'c'), [] )
        self.assertIndices( c02, ('d', 'e'), [s02] )

        c11 = StandardGrammarFamily.create_choice() \
                .start_sequence().constant( 'g' ).end() \
                .start_sequence().constant( 'h' ).end()
        c01.add_expression( c11 )

        self.assertIndices( c0, ('a', 'b', 'c', 'd', 'e', 'g', 'h'), [c02] )
        self.assertIndices( c01, ('a', 'b', 'c', 'g', 'h'), [] )
        self.assertIndices( c02, ('d', 'e'), [s02] )

        c02.as_unbound()

        self.assertIndices( c0, ('a', 'b', 'c', 'g', 'h'), [] )
        self.assertIndices( c01, ('a', 'b', 'c', 'g', 'h'), [] )
        self.assertIndices( c02, ('d', 'e'), [s02] )

        c11.as_unbound()

        self.assertIndices( c0, ('a', 'b', 'c'), [] )
        self.assertIndices( c01, ('a', 'b', 'c'), [] )
        self.assertIndices( c02, ('d', 'e'), [s02] )

        c02.as_unbound( False )
        c11.as_unbound( False )

        self.assertIndices( c0, ('a', 'b', 'c', 'd', 'e', 'g', 'h'), [c02] )
        self.assertIndices( c01, ('a', 'b', 'c', 'g', 'h'), [] )
        self.assertIndices( c02, ('d', 'e'), [s02] )


    def assertIndices(self, expr, indices, noindex):
        self.assertListEqual( expr.get_indexer()._noindex, noindex )
        self.assertCountEqual( expr.get_indexer()._indexed.keys(), indices )
