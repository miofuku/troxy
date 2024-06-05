#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta

from intrpt import Revocable, InterpreterError, Evaluable
from plib.utils import callable_singleton
from plib.utils.builders import FluidMixin


# Called after the parsing of an expression is regarded as successful and irreversible.
# Therefore, these operations must not fail under normal circumstances.
# Return values are not used.
class ValueAction(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def __call__(self, cntxt, expr, value):
        ...

    def __repr__(self):
        return '{}@{:x}({})'.format( self.__class__.__name__, id(self), ', '.join( self._get_value_reprs() ) )

    # Is this really a good idea?
    def _get_value_reprs(self):
        return []

    Empty = None

ValueAction.Empty = callable_singleton( 'Empty', ValueAction, lambda *args: None )


class ValueActionList(ValueAction, FluidMixin):
    __slots__ = '_actions'

    def __init__(self):
        super().__init__()
        self._actions = []

    def add_action(self, action):
        self._actions.append( action )
        return self

    def extend_actions(self, actions):
        self._actions.extend( actions )
        return self

    def remove_action(self, action):
        self._actions.remove( action )
        return self

    def get_actions(self):
        return self._actions

    def store(self, dest, override=False):
        return self.add_action( Store( dest, override ) )

    def store_const(self, dest, value, override=False):
        return self.add_action( Const( Store( dest, override ), value ) )

    def call(self, callable_):
        return self.add_action( Call( callable_ ) )

    def paramcall(self, callable_):
        return self.add_action( ParameterCall( callable_ ) )

    def contextcall(self, callable_):
        return self.add_action( ContextCall( callable_ ) )

    def __call__(self, cntxt, expr, value):
        for act in self._actions:
            if act( cntxt, expr, value )==False:
                return False

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( act ) for act in self._actions]

    def __str__(self):
        return '<{}>'.format( '+'.join( [str( act ) for act in self._actions] ) )


class Const(ValueAction):
    __slots__ = 'value', 'subact'

    def __init__(self, subact, value):
        assert subact is not None
        self.value  = value
        self.subact = subact

    def __call__(self, cntxt, expr, value):
        self.subact._do_call( cntxt, expr, self.value )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.value ), repr( self.subact )]


class RevocableAction(Revocable, ValueAction):
    __slots__ = ()
    def __call__(self, cntxt, expr, value):
        if self._do_call( cntxt, expr, value )!=False:
            cntxt.register_revocable( self )

    @abstractmethod
    def _do_call(self, cntxt, expr, value):
        ...

    @abstractmethod
    def revoke(self, cntxt):
        ...


class Store(RevocableAction):
    __slots__ = 'destination', 'override'

    def __init__(self, dest, override=False):
        self.destination = dest
        self.override    = override

    def _do_call(self, cntxt, expr, value ):
        store = cntxt.args_store
        if not self.override and self.destination in store:
            raise InterpreterError( '{} already has a value: {}'.format( self.destination, store[ self.destination ] ) )
        store[ self.destination ] = value

    def revoke(self, cntxt):
        del cntxt.args_store[ self.destination ]

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.destination ), repr( self.override )]


class OverrideStore(Store):
    __slots__ = ()

    def __init__(self, dest, override=True):
        super().__init__( dest, override )


class StoreList(RevocableAction):
    __slots__ = 'destination'

    def __init__(self, dest):
        self.destination = dest

    def _do_call(self, cntxt, expr, value):
        cntxt.args_store.setdefault( self.destination, [] ).append( value )

    def revoke(self, cntxt):
        list_ = cntxt.args_store[ self.destination ]
        del list_[ -1 ]
        if not list_:
            del cntxt.args_store[ self.destination ]

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.destination )]

# Just to show how the extra store was and is meant.
#    def evaluate(self, cntxt):
#        extra = cntxt.extra_store
#
#        if self.dest in extra:
#            i = extra[ self.dest ]
#        else:
#            i = extra[ self.dest ] = iter( cntxt.args_store[ self.dest ] )
#
#        return next( i )


class Count(RevocableAction):
    __slots__ = 'destination'

    def __init__(self, dest):
        self.destination = dest

    def _get_store(self, cntxt):
        return cntxt.args_store

    def _do_call(self, cntxt, expr, value):
        store  = self._get_store( cntxt )
        curcnt = store.get( self.destination, 0 )
        store[ self.destination ] = curcnt+1

    def revoke(self, cntxt):
        store  = self._get_store( cntxt )
        curcnt = store[ self.destination ]
        if curcnt==1:
            del store[ self.destination ]
        else:
            store[ self.destination ] = curcnt-1

    def get_value(self, cntxt):
        return self._get_store( cntxt ).get( self.destination, 0 )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.destination )]



# It's not named Evaluate because calling it does not lead to an evaluation but to the registration of one.
class Evaluation(Evaluable, Revocable, ValueAction):
    __slots__ = ()

    def __call__(self, cntxt, expr, value):
        if self._do_call( cntxt, expr, value )!=False:
            cntxt.register_evaluation( self )

    def _do_call(self, cntxt, expr, value):
        pass

    def revoke(self, cntxt):
        pass


class Call(Evaluation):
    __slots__ = 'callable'

    def __init__(self, callable_):
        assert callable_ is not None
        self.callable  = callable_

    def evaluate(self, cntxt):
        self.callable( cntxt.args_store )

    def _get_value_reprs(self):
        return super()._get_value_reprs() + [repr( self.callable )]

class ParameterCall(Call):
    __slots__ = ()
    def evaluate(self, cntxt):
        self.callable( **cntxt.args_store )

class ContextCall(Call):
    __slots__ = ()
    def evaluate(self, cntxt):
        self.callable( cntxt )
