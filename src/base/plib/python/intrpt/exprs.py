#!/usr/bin/env python3.4

from _collections import deque
from abc import abstractmethod, ABCMeta
from collections.abc import Sized

from intrpt.evtacts import EventAction, Default
from intrpt.exprfuncs import NestedValueActionMixin, CardinalityMixin, NestedValueProcessorMixin, \
    CompareLowercaseMixin, DefaultValueMixin, StandardActionMixin
from intrpt.format import ExprFormatter
from intrpt.utils import ReferenceCounter
from intrpt.valacts import Store
from plib.utils.builders import FluidMixin, FluidBuilderMixin


class EventJoint(EventAction, Sized, FluidMixin):
    __slots__ = '_typeno', '_listener', '_actions'

    def __init__(self, typeno):
        super().__init__()

        self._typeno   = typeno
        self._listener = None
        self._actions  = []

    def get_type_number(self):
        return self._typeno

    def __len__(self):
        return len( self._actions )

    def add_action(self, action):
        self._actions.append( action )

        if self._listener:
            self._listener.event_action_added( self, action )

        return self

    def remove_action(self, action):
        self._actions.remove( action )

        if self._listener:
            self._listener.event_action_removed( self, action )

        return self

    def default_value(self, dest, value):
        return self.add_action( Default( dest, value ) )

    def __call__(self, cntxt, expr):
        for act in self._actions:
            act( cntxt, expr )
            if cntxt.has_error:
                break

    def set_listener(self, listener):
        self._listener = listener
        return self

    def format(self, exprfmt):
        if self._actions:
            exprfmt = exprfmt.start_group( None, True, False )
            for evt in self._actions:
                if hasattr( evt, 'format' ):
                    evt.format( exprfmt )
                else:
                    exprfmt.string( str( evt ) )
            exprfmt.end()

    def __str__(self):
        return ExprFormatter().format( self ).create_str()

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), self._actions )



class Scope(FluidBuilderMixin):
    _event_type_count   = 3
    _default_value_type = 1

    def __init__(self, expr, config):
        super().__init__( config )

        self._expr         = expr
        self._create_new   = False
        self._outer_scopes = ReferenceCounter()
        self._joints       = [None]*self._event_type_count
        self._outer_joints = [None]*self._event_type_count

    def get_event_joint(self, typeno):
        if typeno<self._event_type_count:
            j = self._joints[ typeno ]
        else:
            j = self._outer_joints[ typeno-self._event_type_count ]

        if j is None:
            j = self._config.create_event_joint( typeno )

            if typeno<self._event_type_count:
                self._joints[ typeno ] = j
                j.set_listener( None if self._create_new else self )
            else:
                self._outer_joints[ typeno-self._event_type_count ] = j
                j.set_listener( self )

        return j

    def enter(self, cntxt):
        assert self._create_new

        if self._joints[ 0 ]:
            self._joints[ 0 ]( cntxt, self._expr )

    def leave(self, cntxt):
        if self._joints[ 1 ]:
            self._joints[ 1 ]( cntxt, self._expr )
        if self._joints[ 2 ]:
            self._joints[ 2 ]( cntxt, self._expr )

    def create_new_scope(self, create_new=True):
        if create_new==self._create_new:
            return self

        # Guard own outer scopes
        self._create_new = None

        if create_new:
            for joint in self._joints:
                if joint:
                    joint.set_listener( None  )

            # Unlink outer scope events belonging to inner joints
            for joint in self._joints:
                if joint and len( joint ):
                    for ref in self._outer_scopes:
                        self._unlink_joint( joint, ref.object )

            # Deregister outer scopes from inner expressions
            for ref in self._outer_scopes:
                for _ in range( ref.count ):
                    self._expr.deregister_scope( ref.object )

            # Register self at inner expressions
            self._expr.register_scope( self )
        else:
            # Deregister self from inner expressions
            self._expr.deregister_scope( self )

            # Register outer scopes at inner expressions
            for ref in self._outer_scopes:
                for _ in range( ref.count ):
                    self._expr.register_scope( ref.object )

            # Link outer scope events belonging to inner joints
            for joint in self._joints:
                if joint and len( joint ):
                    for ref in self._outer_scopes:
                        self._link_joint( joint, ref.object )

            for joint in self._joints:
                if joint:
                    joint.set_listener( self  )

        self._create_new = create_new

        return self

    def get_expression(self):
        return self._expr

    def get_create_new_scope(self):
        return self._create_new

    def configure_enter_events(self):
        return self.get_event_joint( 0 ).caller( self )

    def configure_leave_events(self):
        return self.get_event_joint( 1 ).caller( self )

    def configure_finalize_events(self):
        return self.get_event_joint( 2 ).caller( self )

    def configure_enter_outer_events(self):
        return self.get_event_joint( 3 ).caller( self )

    def configure_leave_outer_events(self):
        return self.get_event_joint( 4 ).caller( self )

    def configure_finalize_outer_events(self):
        return self.get_event_joint( 5 ).caller( self )

    def get_default_value_joint(self):
        return self.get_event_joint( self._default_value_type )

    def register_scope(self, scope):
        if self._create_new is None:
            return True

        if self._outer_scopes.add( scope ).count==1:
            if not self._create_new:
                self._handle_joints( self._joints, scope, self._link_joint )
            self._handle_joints( self._outer_joints, scope, self._link_joint )

        return not self._create_new

    def deregister_scope(self, scope):
        if self._create_new is None:
            return True

        if self._outer_scopes.remove( scope ).count==0:
            if not self._create_new:
                self._handle_joints( self._joints, scope, self._unlink_joint )
            self._handle_joints( self._outer_joints, scope, self._unlink_joint )

        return not self._create_new

    def register_expression(self, expr):
        if self._create_new:
            expr.register_scope( self )
        else:
            for ref in self._outer_scopes:
                expr.register_scope( ref.object )

    def deregister_expression(self, expr):
        if self._create_new:
            expr.deregister_scope( self )
        else:
            for ref in self._outer_scopes:
                expr.deregister_scope( ref.object )

    def register_unbound(self, unbound):
        unbound.activate_on( self.get_event_joint( 3 ) )
        unbound.finish_on( self.get_event_joint( 4 ) )

    def deregister_unbound(self, unbound):
        unbound.remove_activation_from( self.get_event_joint( 3 ) )
        unbound.remove_finishing_from( self.get_event_joint( 4 ) )

    def event_action_added(self, joint, action):
        assert self._create_new==False or joint.get_type_number()>=self._event_type_count

        if len( joint )==1:
            for ref in self._outer_scopes:
                self._link_joint( joint, ref.object )

    def event_action_removed(self, joint, action):
        assert self._create_new==False or joint.get_type_number()>=self._event_type_count

        if len( joint )==0:
            for ref in self._outer_scopes:
                self._unlink_joint( joint, ref.object )

    @classmethod
    def _handle_joints(cls, joints, scope, op):
        for joint in joints:
            if joint and len( joint ):
                op( joint, scope )

    @classmethod
    def _link_joint(cls, joint, scope):
        cls._get_remote_joint( joint, scope ).add_action( joint )

    @classmethod
    def _unlink_joint(cls, joint, scope):
        cls._get_remote_joint( joint, scope ).remove_action( joint )

    @classmethod
    def _get_remote_joint(cls, joint, scope):
        return scope.get_event_joint( joint.get_type_number() % cls._event_type_count )

    def format(self, exprfmt):
        hasi = any( len( j ) for j in self._joints if j )
        haso = any( len( j ) for j in self._outer_joints if j )

        if not hasi and not haso:
            return

        exprfmt = exprfmt.start_group( None, True ).string( '[' )

        if self._create_new:
            exprfmt.string( '>' )

        if hasi:
            self._format_joints( exprfmt, self._joints )

        if haso:
            exprfmt.string( ';' )
            self._format_joints( exprfmt, self._outer_joints )

        exprfmt.string( ']' ).end()

    @classmethod
    def _format_joints(cls, exprfmt, joints):
        if joints[ 0 ]:
            joints[ 0 ].format( exprfmt )
        exprfmt.string( ':' )
        if joints[ 1 ]:
            joints[ 1 ].format( exprfmt )
        if joints[ 2 ]:
            joints[ 2 ].format( exprfmt )

    @abstractmethod
    def _format_value(self, exprfmt):
        ...

    def __str__(self):
        return ExprFormatter().format( self ).create_str()

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    def _get_value_reprs(self):
        return [repr( self._create_new )] + [repr( j ) for j in self._joints] + [repr( self._outer_scopes )]


# TODO: Separate internal (management methods like register_parent) and external creational interfaces
#       (methods invoked by the user to create the expression tree) as well as the parsing interface
#       (Expression directly implements the (external) creational interface + methods like + get_indexer()
#       get_parser(), get_management(); this allows expressions to return the appropriate subcomponents
#       or themselves, if they have to adjust the behaviour.)
class Expression(FluidBuilderMixin, NestedValueActionMixin, CardinalityMixin):
    def __init__(self, parser, config):
        FluidBuilderMixin.__init__( self, config )
        CardinalityMixin.__init__( self )

        self._parser = parser
        self._dec_parser   = None
        self._as_unbound   = False
        self._auto_unbound = True
        self._parents      = ReferenceCounter()

    def register_parent(self, parent):
        self._parents.add( parent )
        self._parser.indexer.register_parent( parent )

    def deregister_parent(self, parent):
        self._parser.indexer.deregister_parent( parent )
        self._parents.remove( parent )

    def register_scope(self, scope):
        return self._parser.scope.register_scope( scope )

    def deregister_scope(self, scope):
        return self._parser.scope.deregister_scope( scope )

    def use_indexer(self, value=True):
        if value:
            self._parser.indexer.activate()
        else:
            self._parser.indexer.deactivate()
        return self

    def get_use_indexer(self):
        return self._parser.indexer.is_active()

    def get_indexer(self):
        return self._parser.indexer

    @property
    def _nested_value_actions(self):
        return self._parser.actions


    def get_config(self):
        return self._config

    def create_new_scope(self, value=True):
        self._parser.scope.create_new_scope( value )
        return self

    def get_create_new_scope(self):
        return self._parser.scope.get_create_new_scope()

    def configure_scope(self):
        return self._parser.scope.caller( self )

    def configure_events(self):
        return self._parser.caller( self )

    def get_events(self):
        return self._parser

    def as_unbound(self, value=True):
        return self._change_as_unbound( value, self._auto_unbound )

    def register_unbound_at_scope(self, value):
        if self._as_unbound:
            return self._change_as_unbound( True, value )
        else:
            self._auto_unbound = value
            return self

    def get_register_unbound_at_scope(self):
        return self._auto_unbound

    def _change_as_unbound(self, value, register_at_scope):
        if value!=self._as_unbound:
            if not value:
                if register_at_scope:
                    self._dec_parser.deregister()
                for ref in self._parents:
                    ref.object.deregister_unbound( self )

            self._as_unbound   = value
            self._auto_unbound = register_at_scope
            self._dec_parser   = None
            self._init_decorator_parser()

            if value:
                if register_at_scope:
                    self._dec_parser.register()
                for ref in self._parents:
                    ref.object.register_unbound( self )
        elif register_at_scope!=self._auto_unbound:
            assert value
            self._auto_unbound = register_at_scope
            if register_at_scope:
                self._dec_parser.register()
            else:
                self._dec_parser.deregister()

        return self

    def get_as_unbound(self):
        return self._as_unbound

    def get_unbound_control(self):
        assert self._as_unbound
        return self._dec_parser

    def cardinality(self, mincnt, maxcnt):
        if mincnt!=self.get_min_count() or maxcnt!=self.get_max_count():
            super().cardinality( mincnt, maxcnt )
            self._init_decorator_parser()

        return self

    def _init_decorator_parser(self):
        if self.get_min_count()==1 and self.get_max_count()==1 and not self._as_unbound:
            self._dec_parser = None
        else:
            if self._dec_parser is None:
                if self._as_unbound:
                    self._dec_parser = self._config.create_unbound_parser( self, self._parser )
                else:
                    self._dec_parser = self._config.create_repetition_parser( self, self._parser )
            self._dec_parser.min_count = self.get_min_count()
            self._dec_parser.max_count = self.get_max_count()


    def parse(self, cntxt):
        return self._parser.parse( cntxt ) if self._dec_parser is None else self._dec_parser.parse( cntxt )


    def format(self, exprfmt):
        exprfmt = exprfmt.start_sequence()

        if self._parser.scope.get_create_new_scope():
            exprfmt.string( '>' )

        self._parser.before_parsing.format( exprfmt )
        self._format_value( exprfmt )
        self._parser.after_parsing.format( exprfmt )

        if self._parser.scope.get_create_new_scope():
            exprfmt.string( '<' )

        self._parser.after_finishing.format( exprfmt )
        self._parser.scope.format( exprfmt )

        exprfmt.end()

    @abstractmethod
    def _format_value(self, exprfmt):
        ...

    def _format_cardinality(self, exprfmt):
        exprfmt.cardinality( self.get_min_count(), self.get_max_count() )

    def __str__(self):
        return ExprFormatter().format( self ).create_str()

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    def _get_value_reprs(self):
        return []



class Terminal(Expression, NestedValueProcessorMixin):
    def __init__(self, parser, config):
        Expression.__init__( self, parser, config )

    @property
    def _nested_value_processors(self):
        return self._parser.processors



class Constant(Terminal, CompareLowercaseMixin):
    def __init__(self, value, config):
        Terminal.__init__( self, self._create_parser( value, config ), config )
        CompareLowercaseMixin.__init__( self )

    def _create_parser(self, value, config):
        return config.create_constant_parser( self, value )

    @property
    def _cmplower_processor_collection(self):
        return self._parser.processors

    def get_value(self):
        return self._parser.value

    def alternative(self, *alterns):
        if alterns:
            self._parser.alternatives.extend( alterns )
            self._parser.indexer.values_added( alterns )
        return self

    def extend_alternatives(self, alterns):
        return self.alternative( *alterns )

    def get_alternatives(self):
        return self._parser.alternatives

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        exprfmt.constant( self._parser.value )



class Variable(Terminal, DefaultValueMixin, StandardActionMixin):
    def __init__(self, name, config):
        Terminal.__init__( self, self._create_parser( name, config ), config )
        DefaultValueMixin.__init__( self )
        StandardActionMixin.__init__( self )

        self.destination( config.get_default_destination( name ) )
        self.standard_action( Store )
        self.use_indexer( False )

    def _create_parser(self, name, config):
        return config.create_variable_parser( self, name )

    def get_name(self):
        return self._parser.name

    def destination(self, dest):
        DefaultValueMixin.destination( self, dest )
        StandardActionMixin.destination( self, dest )
        return self

    def use_default_processors(self, value):
        self._parser.use_default_processors = value
        return self

    def get_use_default_processors(self):
        return self._parser.use_default_processors

    @property
    def _default_value_joint(self):
        return self._parser.scope.get_default_value_joint()

    @property
    def _standard_action_collection(self):
        return self._parser.actions

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        exprfmt.variable( self._parser.name )



class SurrogateExpression(FluidBuilderMixin, metaclass=ABCMeta):
    def __init__(self, config):
        FluidBuilderMixin.__init__( self, config )

        self._scope   = self._config.create_scope( self )
        self._parents = ReferenceCounter()

    @property
    @abstractmethod
    def _subexprs(self):
        ...

    def _register_expression(self, expr):
        expr.register_parent( self )
        self._scope.register_expression( expr )

    def _deregister_expression(self, expr):
        self._scope.deregister_expression( expr )
        expr.deregister_parent( self )

    def register_parent(self, parent):
        self._parents.add( parent )
        for sub in self._subexprs:
            sub.register_parent( parent )

    def deregister_parent(self, parent):
        for sub in self._subexprs:
            sub.deregister_parent( parent )
        self._parents.remove( parent )

    def register_scope(self, scope):
        if self._scope.register_scope( scope ):
            for sub in self._subexprs:
                sub.register_scope( scope )

    def deregister_scope(self, scope):
        if self._scope.deregister_scope( scope ):
            for sub in self._subexprs:
                sub.deregister_scope( scope )

    def get_config(self):
        return self._config

    def get_as_unbound(self):
        return True

    def format(self, exprfmt):
        for sub in self._subexprs:
            sub.format( exprfmt )

    def __str__(self):
        return ExprFormatter().format( self ).create_str()

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    def _get_value_reprs(self):
        return []



class NonterminalMixin:
    def __init__(self):
        self._all_subexprs = deque()

    def _register_expression(self, expr):
        expr.register_parent( self )
        self._parser.scope.register_expression( expr )

    def _deregister_expression(self, expr):
        self._parser.scope.deregister_expression( expr )
        expr.deregister_parent( self )

    def _add_expression(self, expr, prepend=False):
        if prepend:
            self._all_subexprs.appendleft( expr )
        else:
            self._all_subexprs.append( expr )

        if not expr.get_as_unbound():
            if prepend:
                self._parser.subexprs.appendleft( expr )
            else:
                self._parser.subexprs.append( expr )

        self._register_expression( expr )

        if not expr.get_as_unbound():
            self._parser.indexer.subexpression_added( expr )

        return self

    def _remove_expression(self, expr):
        if not expr.get_as_unbound():
            self._parser.indexer.subexpression_removed( expr )

        self._deregister_expression( expr )

        if not expr.get_as_unbound():
            self._parser.subexprs.remove( expr )

        self._all_subexprs.remove( expr )

        return self

    def register_unbound(self, expr):
        self._adjust_unbound( expr, True )

    def deregister_unbound(self, expr):
        self._adjust_unbound( expr, False )

    def _adjust_unbound(self, expr, added):
        cnt = 0
        self._parser.subexprs.clear()

        for sub in self._all_subexprs:
            if not sub.get_as_unbound():
                self._parser.subexprs.append( sub )
            if sub==expr:
                cnt += 1

        assert cnt

        for _ in range( cnt ):
            if not added:
                self._parser.indexer.subexpression_added( expr )
            else:
                self._parser.indexer.subexpression_removed( expr )

    def register_scope(self, scope):
        if super().register_scope( scope ):
            for sub in self._all_subexprs:
                sub.register_scope( scope )

    def deregister_scope(self, scope):
        if super().deregister_scope( scope ):
            for sub in self._all_subexprs:
                sub.deregister_scope( scope )

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        for sub in self._parser.subexprs:
            sub.format( exprfmt )


class Nonterminal(NonterminalMixin, Expression):
    def __init__(self, parser, config):
        NonterminalMixin.__init__( self )
        Expression.__init__( self, parser, config )


class GroupMixin(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def _add_expression(self, expr, prepend):
        ...

    @abstractmethod
    def _remove_expression(self, expr):
        ...

    @abstractmethod
    def get_config(self):
        ...

    def add_expression(self, expr, prepend=False):
        return self._add_expression( expr, prepend )

    def extend_expressions(self, exprs):
        for e in exprs:
            self.add_expression( e )
        return self

    def prepend_expression(self, expr):
        return self.add_expression( expr, True )

    def remove_expression(self, expr):
        return self._remove_expression( expr )

    def get_expressions(self):
        return self._parser.subexprs

    def init_expression(self, expr):
        self.add_expression( expr )
        return expr.caller( self )

    def start_constant(self, value):
        return self.init_expression( self.get_config().create_constant( value ) )

    def constant(self, value, *alterns):
        return self.start_constant( value ).extend_alternatives( alterns ).end()

    def start_variable(self, name):
        return self.init_expression( self.get_config().create_variable( name ) )

    def variable(self, value):
        return self.start_variable( value ).end()



class SubgroupsMixin(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def get_config(self):
        ...

    @abstractmethod
    def init_expression(self):
        ...

    def start_sequence(self):
        return self.init_expression( self.get_config().create_sequence() )

    def start_choice(self):
        return self.init_expression( self.get_config().create_choice() )

    def start_selection(self, name):
        return self.init_expression( self.get_config().create_selection( name ) )



class Sequence(Nonterminal, GroupMixin, SubgroupsMixin):
    def __init__(self, config):
        Nonterminal.__init__( self, self._create_parser( config ), config )

    def _create_parser(self, config):
        return config.create_sequence_parser( self )



class Choice(Nonterminal, GroupMixin, SubgroupsMixin):
    def __init__(self, config):
        Nonterminal.__init__( self, self._create_parser( config ), config )

    def backtracking(self, value=True):
        self._parser.backtracking = value
        return self

    def get_backtracking(self):
        return self._parser.backtracking

    def _create_parser(self, config):
        return config.create_choice_parser( self )

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        exprfmt.start_choice()
        super()._format_value( exprfmt )
        exprfmt.end()



class Selection(Nonterminal, GroupMixin, CompareLowercaseMixin, DefaultValueMixin):
    Default = object()

    def __init__(self, name, config):
        Nonterminal.__init__( self, self._create_parser( config ), config )
        CompareLowercaseMixin.__init__( self )
        DefaultValueMixin.__init__( self )

        self._name = name
        self.default_destination( config.get_default_destination( name ) )

    def _create_parser(self, config):
        return config.create_choice_parser( self )

    @property
    def _cmplower_processor_collection(self):
        raise NotImplementedError()

    def _lowercase_changed(self, value):
        for sub in self._all_subexprs:
            sub.compare_lowercase( value )

    @property
    def _default_value_joint(self):
        return self._parser.scope.get_default_value_joint()

    def get_name(self):
        return self._name

    def default_destination(self, value):
        self._defdest = value
        DefaultValueMixin.destination( self, value )
        return self

    def get_default_destination(self):
        return self._defdest

    def alternative(self, value, *alterns, dest=None, store_value=Default):
        if dest is None:
            dest = self._defdest
        if store_value==Selection.Default:
            store_value = value

        return super().start_constant( value ) \
                            .extend_alternatives( alterns ) \
                            .compare_lowercase( self.get_compare_lowercase() ) \
                            .store_const( dest, store_value ) \
                            .end()

    def add_alternatives(self, *values):
        return self.extend_alternatives( values )

    def extend_alternatives(self, values):
        for value in values:
            self.constant( value )
        return self

    def start_others(self, varname, dest=None):
        if dest is None:
            dest = self._defdest

        return super().start_variable( varname ).store_default( False ).store( dest )

    def others(self, varname, dest=None):
        return self.start_others( varname, dest=dest ).end()

    def _format_value(self, exprfmt):
        self._format_cardinality( exprfmt )
        exprfmt.start_choice()
        super()._format_value( exprfmt )
        exprfmt.end()
