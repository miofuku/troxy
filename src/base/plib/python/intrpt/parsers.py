#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta

from intrpt.evtacts import EventAction
from intrpt import Revocable
from intrpt.valacts import Count
from plib.utils.builders import FluidMixin
from _collections import deque


class ScopeElement(metaclass=ABCMeta):
    class ScopeAction(EventAction):
        __slots__ = '_element'

        def __init__(self, element):
            self._element = element

        def _get_value_reprs(self):
            return super()._get_value_reprs() + [repr( self._element )]

        def format(self, exprfmt):
            exprfmt.string( self._symbol )
            self._element.format( exprfmt )


    class Activate(ScopeAction, Revocable):
        _symbol = '+'

        __slots__ = '_is_unbound'

        def __init__(self, element, is_unbound=False):
            super().__init__( element )
            self._is_unbound = is_unbound

        def __call__(self, cntxt, expr):
            cntxt.register_revocable( self )
            cntxt.current_scope.activate_element( self._element, self._is_unbound )

        def revoke(self, cntxt):
            cntxt.current_scope.deactivate_element( self._element, self._is_unbound  )


    class Deactivate(ScopeAction, Revocable):
        _symbol = '-'

        __slots__ = '_is_unbound'

        def __init__(self, element, is_unbound=False):
            super().__init__( element )
            self._is_unbound = is_unbound

        def __call__(self, cntxt, expr):
            cntxt.current_scope.deactivate_element( self._element, self._is_unbound  )
            cntxt.register_revocable( self )

        def revoke(self, cntxt):
            cntxt.current_scope.activate_element( self._element, self._is_unbound  )


    class Finish(ScopeAction):
        _symbol = '!'

        __slots__ = ()

        def __call__(self, cntxt, expr):
            self._element.finish( cntxt, expr )

    __slots__ = '_act_act', '_dea_act', '_fin_act'

    def __init__(self):
        self._init_scope_element()

    def _init_scope_element(self, act_act=None, dea_act=None, fin_act=None):
        self._act_act = act_act or ScopeElement.Activate( self )
        self._dea_act = dea_act or ScopeElement.Deactivate( self )
        self._fin_act = fin_act or ScopeElement.Finish( self )

    def activate_on(self, joint):
        joint.add_action( self._act_act )

    def remove_activation_from(self, joint):
        joint.remove_action( self._act_act )

    def deactivate_on(self, joint):
        joint.add_action( self._dea_act )

    def remove_deactivation_from(self, joint):
        joint.remove_action( self._dea_act )

    def finish_on(self, joint):
        joint.add_action( self._fin_act )

    def remove_finishing_from(self, joint):
        joint.remove_action( self._fin_act )

    def activate(self, cntxt):
        self._act_act( cntxt, self )

    def deactivate(self, cntxt):
        self._dea_act( cntxt, self )

    def finish(self, cntxt, expr):
        if cntxt.current_scope.is_element_active( self ):
            self._do_finish( cntxt, expr )
            if not cntxt.has_error:
                self.deactivate( cntxt )

    @abstractmethod
    def _do_finish(self, cntxt):
        ...

    def is_active(self, cntxt):
        return cntxt.current_scope.is_element_active( self )



class ScopeParseContext:
    __slots__ = '_cntxt', '_scope', '_is_entered', '_active_elems', '_active_elems_list', '_active_ubs_new', \
                '_active_ubs_indexer', '_parsing_ubs', '_config'

    def __init__(self, cntxt, scope, config):
        self._cntxt  = cntxt
        self._scope  = scope
        self._config = config

        self._is_entered = False

        self._active_elems       = set()
        self._active_elems_list  = []
        self._active_ubs_new     = None
        self._active_ubs_indexer = None
        self._parsing_ubs        = False

    def get_indexer(self):
        return self._active_ubs_indexer

    def raise_event(self, expr, joint):
        if joint:
            joint( self._cntxt, expr )

        if self._active_ubs_new and not self._parsing_ubs and self._cntxt.current is not None:
            succ = self._parse_next_unbound( self._active_ubs_new )
            self._active_ubs_new.clear()
            if succ:
                self.parse_unbounds()


    def parse_unbounds(self):
        if self._parsing_ubs:
            return

        succ = True

        if self._active_ubs_indexer:
            while self._cntxt.current is not None and succ:
                if not self._parse_next_unbound( self._active_ubs_indexer.get_not_fully_indexed() ):
                    if not self._active_ubs_new:
                        succ = False
                    else:
                        succ = self._parse_next_unbound( self._active_ubs_new )
                        self._active_ubs_new.clear()


    def _parse_next_unbound(self, unbounds):
        assert self._cntxt.current is not None and not self._parsing_ubs

        succ = False
        self._parsing_ubs = True

        indexed = self._active_ubs_indexer.get( self._cntxt.current, None )
        if indexed:
            succ = indexed.parse( self._cntxt )>0
        else:
            for ub in unbounds:
                if ub.parse( self._cntxt )>0:
                    succ = True
                    break
                elif self._cntxt.has_error:
                    break

        self._parsing_ubs = False
        return succ

    def start(self):
        self._cntxt.enter_scope( self )
        return self

    def is_entered(self):
        return self._is_entered

    def enter(self):
        if self._scope:
            self._scope.enter( self._cntxt )
        self._is_entered = True
        return self

    def leave(self):
        if self._scope:
            self._scope.leave( self._cntxt )
        self._is_entered = False
        return self

    def end(self):
        assert not self._active_elems or self._cntxt.has_error, repr( self._active_elems )

        ret = self._cntxt.leave_scope()
        assert ret==self

        return self


    def is_element_active(self, element):
        return element in self._active_elems


    def activate_element(self, element, is_unbound):
        assert element not in self._active_elems, element

        self._active_elems.add( element )
        self._active_elems_list.append( element )
        if is_unbound:
            if not self._active_ubs_indexer:
                self._active_ubs_new     = set()
                self._active_ubs_indexer = self._config.create_scope_indexer( self )

            self._active_ubs_new.add( element )

            element.register_parent( self )
            self._active_ubs_indexer.subexpression_added( element )


    def deactivate_element(self, element, is_unbound):
        assert element in self._active_elems, element

        self._active_elems.remove( element )
        self._active_elems_list.remove( element )
        if is_unbound:
            self._active_ubs_new.discard( element )

            self._active_ubs_indexer.subexpression_removed( element )
            element.deregister_parent( self )




class ParserBase:
    def __init__(self, expr, indexer, config):
        self.expr    = expr
        self.config  = config
        self.indexer = indexer

    @property
    @abstractmethod
    def scope(self):
        ...

    def get_indexer(self):
        return self.indexer

    def parse(self, cntxt):
        assert not cntxt.has_error

        rootscope = self._create_scope_cntxt( cntxt, None ) if not cntxt.current_scope else None
        exprscope = self._create_scope_cntxt( cntxt, self.scope ) if self.scope.get_create_new_scope() else None

        value    = None
        state    = None
        startpos = cntxt.position

        self._prepare_parsing( cntxt )

        if not cntxt.has_error:
            while True:
                skip, value, state = self._do_parse( cntxt, value, state )

                if skip or cntxt.has_error:
                    break

                self._finish_parsing( cntxt, value )

                if self.scope.get_create_new_scope():
                    cntxt.current_scope.leave()

                if not (cntxt.has_error and self._can_retry( cntxt, value, state )):
                    break

        if exprscope:
            exprscope.end()

        if cntxt.has_error:
            self._on_error( cntxt, value )
        else:
            if not skip and self._is_successful( cntxt, value, state ):
                self._on_success( cntxt, value )

                if not cntxt.current_scope.is_entered():
                    cntxt.current_scope.enter()

            # We re-entered the previous scope and made progress meanwhile, thus we have to check
            # if unbounds of the previous scope can now be parsed.
            if cntxt.position!=startpos and exprscope:
                cntxt.current_scope.parse_unbounds()

        if rootscope:
            rootscope.leave().end()

        return value

    def _create_scope_cntxt(self, cntxt, scope):
        return self.config.create_scope_parse_context( cntxt, scope ).start()

    def _prepare_parsing(self, cntxt):
        pass

    @abstractmethod
    def _do_parse(self, cntxt, last_sub, last_trial):
        ...

    def _finish_parsing(self, cntxt, value):
        pass

    def _can_retry(self, cntxt, value, state):
        return False

    def _on_error(self, cntxt, value):
        pass

    def _is_successful(self, cntxt, value, state):
        return True

    # This expression can now be considered as successfully parsed. Following expressions are allowed
    # to set the error state of the context, but this has no consequences for the return value
    # of this expression.
    def _on_success(self, cntxt, value):
        pass



class ExpressionParser(ParserBase, FluidMixin):
    def __init__(self, expr, indexer, config):
        ParserBase.__init__( self, expr, indexer, config )
        FluidMixin.__init__( self )

        self._scope  = config.create_scope( self.expr )
        self.joints  = [None]*3
        self.actions = config.create_value_action_collection()

    def get_event_joint(self, typeno):
        j = self.joints[ typeno ]

        if j is None:
            j = self.joints[ typeno ] = self.config.create_event_joint( typeno )

        return j

    def configure_before_parsing(self): # within inner scope
        return self.get_event_joint( 0 ).caller( self )

    def configure_after_parsing(self): # within inner scope
        return self.get_event_joint( 1 ).caller( self )

    def configure_after_finishing(self): # within outer scope
        return self.get_event_joint( 2 ).caller( self )

    @property
    def scope(self):
        return self._scope

    @property
    def before_parsing(self):
        return self.get_event_joint( 0 )

    @property
    def after_parsing(self):
        return self.get_event_joint( 1 )

    @property
    def after_finishing(self):
        return self.get_event_joint( 2 )

    def _prepare_parsing(self, cntxt):
        super()._prepare_parsing( cntxt )

        cntxt.current_scope.raise_event( self.expr, self.joints[ 0 ] )

    def _finish_parsing(self, cntxt, value):
        super()._finish_parsing( cntxt, value )

        cntxt.current_scope.raise_event( self.expr, self.joints[ 1 ] )

    def _on_success(self, cntxt, value):
        super()._on_success( cntxt, value )
        self.actions( cntxt, self.expr, value)

        cntxt.current_scope.raise_event( self.expr, self.joints[ 2 ] )



class RepetitionParser(ParserBase):
    def __init__(self, expr, subparser, config):
        super().__init__( expr, subparser.indexer, config )
        self.min_count = 1
        self.max_count = 1

        self.subparser = subparser

    def _create_indexer(self, config):
        return None

    @property
    def scope(self):
        return self.subparser.scope

    def _do_parse(self, cntxt, last_value, last_state):
        return self._do_parse_minmax( cntxt, self.min_count, self.max_count)

    def _do_parse_minmax(self, cntxt, min_count, max_count):
        cnt = 0

        while max_count is None or cnt<max_count:
            trial = cntxt.start_trial( self.expr )

            self.subparser.parse( cntxt )

            if not cntxt.has_error:
                cnt += 1
                trial.finish( True )
            else:
                if cnt<min_count or not trial.rollback():
                    trial.finish( False )
                    return False, cnt, None
                elif cnt==0:
                    # We will skip this expression and will therefore not called again by our base class.
                    trial.finish( True )
                    return True, 0, None
                else: # cnt>0 and cnt>=min_count; now we have to wait for _finish_parsing()
                    return False, cnt, trial

        return cnt==0, cnt, None

    def _can_retry(self, cntxt, sub, trial):
        if trial:
            trial.finish( False )
        return False

    def _is_successful(self, cntxt, cnt, trial):
        if trial:
            trial.finish( True )
        return True



class UnboundParser(RepetitionParser, ScopeElement):
    class _UnboundCount(Count):
        __slots__ = ()

        def _get_store(self, cntxt):
            return cntxt.exprs_store

    def __init__(self, expr, subparser, config):
        super().__init__( expr, subparser, config )

        self._init_scope_element(
                ScopeElement.Activate( self, True ),
                ScopeElement.Deactivate( self, True ),
                ScopeElement.Finish( self ) )
        self._count = self._UnboundCount( self )

    def register_parent(self, parent):
        self.indexer.register_parent( parent )

    def deregister_parent(self, parent):
        self.indexer.deregister_parent( parent )

    def register(self):
        self.subparser.scope.register_unbound( self )

    def deregister(self):
        self.subparser.scope.deregister_unbound( self )

    def _do_finish(self, cntxt, expr):
        # If the unbound is required exactly once and if it is still active,
        # it has apparently not parsed yet.
        if self.min_count>0 and (not self._count or self._count.get_value( cntxt )<self.min_count):
            cntxt.on_error( self.expr )

    def parse(self, cntxt):
        idx = (self, cntxt.position)
        cnt = 0

        if idx not in cntxt.exprs_store:
            cnt = super().parse( cntxt )

            if cnt==0:
                cntxt.exprs_store[ idx ] = 0

        return cnt

    def _do_parse(self, cntxt, last_value, last_state):
        return self._do_parse_minmax( cntxt, 0, 1 )

    def _on_success(self, cntxt, cnt):
        self.decrement_count( cntxt )

        return super()._on_success( cntxt, cnt )

    def is_available(self, cntxt, cnt):
        if not cntxt.current_scope.is_element_active( self ):
            return False
        elif self.max_count is None:
            return True
        elif self.max_count<=1:
            return cnt<=self.max_count
        else:
            return cnt <= self.max_count-self._count.get_value( cntxt )

    def decrement_count(self, cntxt):
        assert self.is_available( cntxt, 1 )

        if self.max_count is not None and self.max_count<=1:
            self.deactivate( cntxt )
        else:
            self._count( cntxt, self, 1 )

            if self.max_count is not None and self._count.get_value( cntxt )==self.max_count:
                self.deactivate( cntxt )

    def format(self, exprfmt):
        exprfmt.string( str( id(self.expr) ) )



class TerminalParser(ExpressionParser):
    def __init__(self, expr, indexer, config):
        super().__init__( expr, indexer, config )

        self.processors = config.create_value_processor_collection()

    def _do_parse(self, cntxt, last_value, last_state):
        value = None

        if cntxt.current is None:
            cntxt.on_error( self.expr )
        else:
            try:
                value = self._parse_value( cntxt, cntxt.current )
            except ValueError as err:
                cntxt.on_value_error( self.expr, suberrs=err )

            if not cntxt.has_error:
                cntxt.forward()

        return False, value, None

    def _parse_value(self, cntxt, value):
        return self.processors( cntxt, self.expr, value, 0 )

    def _finish_parsing(self, cntxt, value):
        super()._finish_parsing( cntxt, value )
        if not cntxt.has_error:
            cntxt.current_scope.parse_unbounds()



class ConstantParser(TerminalParser):
    def __init__(self, expr, value, config):
        super().__init__( expr, self._create_indexer( expr, config ), config )

        self.value = value
        self.alternatives = []

    def _create_indexer(self, expr, config):
        return config.create_constant_indexer( expr, self )

    def _parse_value(self, cntxt, value):
        value = super()._parse_value( cntxt, value )

        if not cntxt.has_error and not self._check( cntxt, value ):
            cntxt.on_error( self.expr )

        return self.value

    def _check(self, cntxt, value):
        return value==self.value or value in self.alternatives



class VariableParser(TerminalParser):
    def __init__(self, expr, name, config):
        super().__init__( expr, self._create_indexer( expr, config ), config )

        self.name = name
        self.use_default_processors = True

    def _create_indexer(self, expr, config):
        return config.create_variable_indexer( expr, self )

    def _parse_value(self, cntxt, value):
        if self.use_default_processors:
            value = self.config.get_default_variable_processors()( cntxt, self.expr, value, 0 )

            if cntxt.has_error:
                return value

        return self.processors( cntxt, self.expr, value, 0 )


class NonterminalParser(ExpressionParser):
    def __init__(self, expr, indexer, config):
        super().__init__( expr, indexer, config )

        self.subexprs = deque()



class SequenceParser(NonterminalParser):
    def __init__(self, expr, config):
        super().__init__( expr, self._create_indexer( expr, config ), config )

    def _create_indexer(self, expr, config):
        return config.create_sequence_indexer( expr, self )

    def _do_parse(self, cntxt, last_value, last_state):
        for sub in self.subexprs:
            sub.parse( cntxt )

            if cntxt.has_error:
                return False, sub, None

        return False, None, None



class ChoiceParser(NonterminalParser):
    def __init__(self, expr, config):
        super().__init__( expr, self._create_indexer( expr, config ), config )

        self.backtracking = None

    def _create_indexer(self, expr, config):
        return config.create_choice_indexer( expr, self )

    def _do_parse(self, cntxt, last_sub, last_trial):
        if not last_sub and cntxt.current is not None and cntxt.current in self.indexer:
            sub = self.indexer[ cntxt.current ]

            sub.parse( cntxt )

            return False, sub, None
        else:
            trial = cntxt.start_trial( self.expr, self.backtracking )

            for sub in self.subexprs:
                # Do we have another round? Then we have to move forward until we get to the position where
                # the last round stopped.
                if last_sub:
                    if last_sub==sub:
                        last_sub = None
                    continue

                sub.parse( cntxt )

                if not cntxt.has_error:
                    return False, sub, trial
                elif not trial.rollback():
                    break

            trial.finish( False )
            assert cntxt.has_error

            return False, None, None

    def _can_retry(self, cntxt, sub, trial):
        return trial is not None and trial.rollback()

    def _is_successful(self, cntxt, sub, trial):
        if trial is not None:
            trial.finish( True )
        return True
