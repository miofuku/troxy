#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta

from plib.collections import AttributeDict


class Evaluable(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def evaluate(self, cntxt):
        ...


class Revocable(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def revoke(self, cntxt):
        ...


class Trial(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def rollback(self):
        ...

    @abstractmethod
    def finish(self, successful):
        ...



# Should not happen. Configuration or implementation error.
class InterpreterError(Exception):
    pass


class EvalError(Exception):
    def __init__(self, tokens, suberrs=None, msg=None):
        self.tokens  = tokens
        self.suberrs = suberrs
        self.msg     = msg

    def __eq__(self, other):
        return type(other) is type(self) and other.__dict__==self.__dict__

    def __ne__(self, other):
        return not self.__eq__( other )

    def __hash__(self):
        return hash( tuple( sorted( self.__dict__.items() ) ) )

    def __str__(self):
        return self.msg if self.msg is not None else super().__str__()


class ExprError(EvalError):
    def __init__(self, pos, tokens, expr, suberrs=None, level=0, msg=None):
        super().__init__( tokens, suberrs, msg )
        assert pos is not None
        self.pos   = pos     # When pos>=#tokens, tokens seem to be missing
        self.expr  = expr    # When None, too many tokens were found.
        self.level = level

    def __str__(self):
        if self.msg is None:
            if self.pos>=len( self.tokens ):
                self.msg = 'Missing arguments for {}'.format( self.expr )
            else:
                self.msg = 'Unexpected argument at pos {}: {}'.format( self.pos, self.tokens[ self.pos ] )
        return self.msg


class ExprValueError(ExprError):
    def __init__(self, pos, tokens, expr, suberr, level=0, msg=None):
        assert pos<len( tokens )

        super().__init__( pos, tokens, expr, (suberr,) if suberr else None, level, msg )

    def __str__(self):
        if self.msg is None and self.suberrs:
            self.msg = 'Unexpected argument at pos {}: {}'.format( self.pos, self.suberrs[ 0 ] )

        return self.msg if self.msg else super().__str__()


# TODO: Evaluation should be revised and extended. A similar solution as for actions would be conceivable.
#       A store can be used as register file and stack according to the needs. Further, the interpreter
#       should be called to evaluate a context.

class IntrptContext:
    def __init__(self, intrpter, tokens, backtracking_default=False):
        self.intrpter      = intrpter
        self.tokens        = tuple( tokens )
        self.backtracking_default = backtracking_default

        self._success      = None
        self._is_evald     = False
        self._result       = None
        self._pos          = 0
        # It would be less error-prone if errors occurred during parsing were raised and not stored.
        # However, in some sense, a lot of these errors belong to the normal control flow.
        # We don't have a fixed syntax, there are choices and alternative ways. If an unexpected token
        # is encountered, maybe we are just on the wrong path. Unexpected tokens are not an exception,
        # they are expected, so to speak. The question is, what were the implications for the performance?
        self._error        = None
        # (hello world){1,3} and 'hello world hello !!' -> '!!' is presumably wrong.
        # Without saving the error as potential error, parsing would stop with 'hello world'
        # and unexpected 'hello !!'.
        self._poterror     = None

        self._scopes       = []
        self._evals        = []
        self._revocables   = []
        self._args_store   = AttributeDict()
        self._exprs_store  = AttributeDict()

    @property
    def env(self):
        return self.intrpter.env

    @property
    def args_store(self):
        return self._args_store

    @property
    def exprs_store(self):
        return self._exprs_store


    @property
    def position(self):
        return self._pos

    @property
    def current(self):
        return self.tokens[ self._pos ] if self._pos<len( self.tokens ) else None

    def forward(self):
        self._pos = min( self._pos+1, len( self.tokens ) )


    def enter_scope(self, scope):
        self._scopes.append( scope )

    def leave_scope(self):
        return self._scopes.pop()

    @property
    def current_scope(self):
        return self._scopes[ -1 ] if self._scopes else None


    @property
    def has_error(self):
        return self._error is not None

    @property
    def error(self):
        return self._error

    @error.setter
    def error(self, value):
        self._error = value

    def on_error(self, expr, pos=None, suberrs=None, level=0, msg=None):
        self._error = self.create_error( expr, pos, suberrs, level, msg )

    def on_value_error(self, expr, pos=None, suberrs=None, level=0, msg=None):
        if msg is None and isinstance( suberrs, ValueError ) and str( suberrs ):
            msg = str( suberrs )

        self._error = self.create_error( expr, pos, suberrs, level, msg, ExprValueError )

    def create_error(self, expr, pos=None, suberrs=None, level=0, msg=None, cls=ExprError):
        if pos is None:
            pos = self._pos

        return cls( pos, self.tokens, expr, suberrs, level, msg )


    def register_evaluation(self, eval_):
        self._evals.append( eval_ )
        self._revocables.append( eval_ )

    def register_revocable(self, revocable):
        self._revocables.append( revocable )

    @property
    def is_finalized(self):
        return self._is_finid

    def finalize(self):
        if self._success is not None:
            raise InterpreterError( 'Context has been already finalized.' )

        self._success = False

        if self._error:
            raise self._error
        elif self._pos<len( self.tokens ):
            self._on_left_tokens()
        else:
            self._success = True

    @property
    def is_evaluated(self):
        return self._is_evald

    def evaluate(self):
        if self._is_evald:
            raise InterpreterError( 'Context has been already evaluated.' )
        if self._success is None:
            self.finalize()
        if not self._success:
            raise InterpreterError( 'Parsing was not successful.' )

        self._is_evald = True

        self._result = self.intrpter._evaluate_context( self )

        return self

    def get_result(self):
        if not self._is_evald:
            self.evaluate()

        return self._result

    def _set_error(self, error, is_potential=False):
        if is_potential:
            self._poterror = error
        else:
            self._error = error

    def _on_left_tokens(self):
        if self._poterror and self._poterror.pos>=self._pos:
            raise self._poterror
        else:
            raise ExprError( self._pos, self.tokens, None )


    def start_trial(self, expr, backtracking=None):
        if backtracking is None:
            backtracking = self.backtracking_default

        if backtracking:
            return IntrptContext._TrialBT( self, expr )
        else:
            return IntrptContext._Trial( self, expr )


    class _Trial(Trial):
        def __init__(self, cntxt, expr):
            assert not cntxt._error

            self._cntxt       = cntxt
            self._expr        = expr
            self._start_pos   = cntxt._pos
            self._start_revs  = len( cntxt._revocables )
            self._start_evals = len( cntxt._evals )
            self._col_errs    = []


        @property
        def start_pos(self):
            return self._start_pos


        def rollback(self):
            self._save_error()

            self._cntxt._error = None

            if self._cntxt._pos==self._start_pos:
                self._revoke()
                return True
            else:
                return False


        def finish(self, successful):
            self._save_error()

            if not successful:
                if self._cntxt._pos==self._start_pos:
                    self._revoke()
                if not self._cntxt.has_error:
                    self._cntxt.error = self._create_error( self._col_errs )
            else:
                errpos = self._get_errpos( self._col_errs )
                errlev = self._get_errlev( self._col_errs )
                potpos = self._cntxt._poterror.pos if self._cntxt._poterror else -1
                potlev = self._cntxt._poterror.level if self._cntxt._poterror else -1

                # The trial was successful thus we do not have any real error but we have to keep the collected
                # error with the greatest position as a potential one.
                if errpos>=self._start_pos and (errpos>potpos or errpos==potpos and errlev>=potlev):
                    self._set_cntxt_error( self._col_errs, True )

        def _revoke(self):
            while len( self._cntxt._revocables )>self._start_revs:
                rev = self._cntxt._revocables.pop()
                rev.revoke( self._cntxt )
                if self._cntxt._evals and self._cntxt._evals[ -1 ]==rev:
                    self._cntxt._evals.pop()
            assert len( self._cntxt._evals )==self._start_evals


        def _get_errpos(self, errorlist):
            return errorlist[ 0 ].pos if errorlist else -1

        def _get_errlev(self, errorlist):
            return errorlist[ 0 ].level if errorlist else -1


        def _save_error(self):
            if self._cntxt._error:
                self._do_save_error( self._cntxt._error, self._col_errs )


        def _do_save_error(self, error, errorlist):
            curerrpos = self._get_errpos( errorlist )
            curerrlev = self._get_errlev( errorlist )

            if error.pos>curerrpos or error.pos==curerrpos and error.level>=curerrlev:
                if error.pos>curerrpos or error.level>curerrlev:
                    errorlist.clear()
                if error not in errorlist:
                    errorlist.append( error )


        def _set_cntxt_error(self, errorlist, is_potential):
            err = self._create_error( errorlist )

            self._cntxt._set_error( err, is_potential )

        def _create_error(self, errorlist):
            if len( errorlist )==1:
                return errorlist[ 0 ]
            else:
                return self._cntxt.create_error( self._expr, self._get_errpos( errorlist ), tuple( errorlist ) )

    # Even backtracking cannot, or should not, detect situations where one option is entirely a prefix of
    # another option: (hello | hello world). To detect such constructs, we would need to explore all paths
    # even if one path already matched. However, if the order is correct (hello world | world) it should work
    # anyway. Also cases where two options share a prefix but where no option starts completely with the other
    # one should be handled just fine.
    class _TrialBT(_Trial):
        def __init__(self, cntxt, expr):
            super().__init__( cntxt, expr )

            self._start_pot   = cntxt._poterror
            self._col_pots    = []

            if self._start_pot:
                self._col_pots.append( self._start_pot )

        def rollback(self):
            self._save_error()

            self._revoke()

            self._cntxt._pos      = self._start_pos
            self._cntxt._error    = None
            self._cntxt._poterror = self._start_pot

            return True


        def finish(self, successful):
            self._save_error()

            if not successful:
                self._set_cntxt_error( self._col_errs, False )
            else:
                errpos = self._get_errpos( self._col_errs )
                errlev = self._get_errlev( self._col_errs )
                potpos = self._get_errpos( self._col_pots )
                potlev = self._get_errlev( self._col_pots )

                # The trial was successful thus we do not have any real error but we have to keep the collected
                # error with the greatest position as a potential one.
                if errpos>=self._start_pos and (errpos>potpos or errpos==potpos and errlev>=potlev):
                    self._set_cntxt_error( self._col_errs, True )
                elif potpos>=self._start_pos:
                    self._set_cntxt_error( self._col_pots, True )


        def _save_error(self):
            super()._save_error()

            if self._cntxt._poterror and self._cntxt._poterror!=self._start_pot:
                self._do_save_error( self._cntxt._poterror, self._col_pots )


# Is there a difference between "--opt" and --opt? If so, this may lead a dedicated token type.
#
# TODO: A lexer is just a (simple) grammar typically parsed as a whole or at least by other means
#       than the flexible interpreter pattern here. Eventually, this could lead to the introduction of
#       a grammar interface and a chain or hierarchy of grammars where tokens are passed as inputs,
#       processed by a particular grammar, and mapped to output tokens in turn used as inputs for
#       a subsequent grammar. For example: ('a:a b,b 123 # comment',) -> ('a:a', 'b,b', '123') ->
#       ('a', ':', 'a', 'b', ',', 'b', '123') |  (('a', ':', 'a'), ('b', ',', 'b'), ('123',))
#
#       Another outcome of this notion is that tokens are somewhat degraded. They are not atomic
#       anymore but have a (parsable) structure themselves. For instance, an option can be seen as
#       ['--opt' | '-o'] or ['--' 'opt' | '-' 'o'], a sequence of constants. This could lead to the
#       introduction of explicit token delimiters as discrete expressions: ('--' 'opt') vs. ('--' - <file>)
#       where - denotes token boundaries. To create indices, sequences would check if all subexpressions
#       up to a token delimiter are constants or can register indices. In that case, all possible combinations
#       could be calculated and registered as indices to improve performance.
#       In that course, checking prefixes for options could become obsolete:
#       ('--' ('opt0' | 'opt1)) | ('-' ('o0' | 'o1'))
#       A flag group? Is a '-' followed by a number of optional constants (together with a condition not
#       present yet: At least one constant out of this group has to be there.)
#
#       Although both rely on grammars, lexers and interpreters nevertheless remain distinct. Lexers
#       could also be compilers, they transform inputs into outputs but do not evaluate the semantics.
class Lexer(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def tokenize(self):
        ...


class SplitLexer(Lexer):
    def tokenize(self, str_):
        return str_.split()



class Intrpter:
    def __init__(self, grammar, env=None, lexer=SplitLexer(), backtracking_default=False, cntxtclass=IntrptContext):
        self.env        = env
        self.grammar    = grammar
        self.lexer      = lexer
        self.backtracking_default = backtracking_default
        self.cntxtclass = cntxtclass

    def _evaluate_context(self, cntxt):
        res = None
        for expr in cntxt._evals:
            res = expr.evaluate( cntxt )
        return res

    def parse(self, tokens):
        return self._do_parse( tokens, self.grammar )

    def evaluate_string(self, str_):
        return self.parse( self.lexer.tokenize( str_ ) ).evaluate()

    def _create_context(self, tokens):
        return IntrptContext( self, tokens, backtracking_default=self.backtracking_default)

    def _do_parse(self, tokens, grammar):
        cntxt = self._create_context( tokens )

        grammar.parse( cntxt )

        cntxt.finalize()

        return cntxt
