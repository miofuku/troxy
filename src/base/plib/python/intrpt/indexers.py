#!/usr/bin/env python3.4

from abc import abstractmethod, ABCMeta
from collections.abc import Container

from intrpt.utils import ReferenceCounter
from plib.utils import Value


class ExpressionIndexer(metaclass=ABCMeta):
    def __init__(self, principal, parser):
        self._principal     = principal
        self._parser        = parser
        self._is_active     = True
        self._parents       = ReferenceCounter()
        self._index_req_cnt = 0

    @property
    def is_active(self):
        return self._is_active

    def activate(self):
        self._change_active_state( True )

    def deactivate(self):
        self._change_active_state( False )

    def _change_active_state(self, value):
        if value!=self._is_active:
            self._is_active = value
            self._active_state_changed( value )

    def register_parent(self, parent):
        self._parents.add( parent ).data = 0

    def deregister_parent(self, parent):
        self._parents.remove( parent )

    @abstractmethod
    def get_is_fully_indexed(self):
        ...

    def get_indices(self):
        if not self._is_active:
            return ()
        else:
            return self._do_get_indices()

    def change_index_subscription(self, parent, add):
        ref = self._parents.get( parent )

        cnt = 1 if add else -1
        ref.data += cnt
        self._index_req_cnt += cnt

        assert ref.data==0 or ref.data==1

        return self.get_indices()

    def propagate_indices(self, subexpr, indices, added):
        raise NotImplementedError()

    @abstractmethod
    def _do_get_indices(self):
        ...

    def _active_state_changed(self, value):
        self._index_state_changed( self._do_get_indices(), value )

    def _index_state_changed(self, indices, added):
        if not self._index_req_cnt:
            return

        for ref in self._parents:
            if ref.data:
                ref.object.get_indexer().propagate_indices( self._principal, indices, added )


class ConstantIndexer(ExpressionIndexer):
    def values_added(self, values):
        self._index_state_changed( values, True )

    def values_removed(self, values):
        self._index_state_changed( values, False )

    def get_is_fully_indexed(self):
        return self._is_active

    def _do_get_indices(self):
        idxs = list( self._parser.alternatives )
        idxs.append( self._parser.value )
        return idxs


class VariableIndexer(ExpressionIndexer):
    def get_is_fully_indexed(self):
        return False

    def _do_get_indices(self):
        return ()


class GroupIndexer(ExpressionIndexer):
    def subexpression_added(self, expr):
        self._subexpr_changed( expr, True )

    def subexpression_removed(self, expr):
        self._subexpr_changed( expr, False )

    @property
    def _subexprs(self):
        return self._parser.subexprs

    @abstractmethod
    def _subexpr_changed(self, expr, added):
        ...

    def _active_state_changed(self, value):
        if not value:
            self._change_and_propagate_index_subscriptions( False )

        super()._active_state_changed( value )

        if value:
            self._change_and_propagate_index_subscriptions( True )

    def _change_and_propagate_index_subscriptions(self, add):
        self._index_state_changed( self._change_index_subscriptions( add ), add )

    @abstractmethod
    def _change_index_subscriptions(self, add):
        ...


class SequenceIndexer(GroupIndexer):
    def __init__(self, principal, parser):
        super().__init__( principal, parser )

        self._pivot = None
        self._is_fully_indexed = True

    def get_is_fully_indexed(self):
        return self._is_fully_indexed and self._is_active

    def change_index_subscription(self, parent, add):
        if not add and self._index_req_cnt==1:
            ridxs = self._change_index_subscriptions( False )
        else:
            ridxs = ()

        idxs = super().change_index_subscription( parent, add )
        idxs.extend( ridxs )

        if add and self._index_req_cnt==1:
            idxs.extend( self._change_index_subscriptions( True ) )

        return idxs

    def propagate_indices(self, subexpr, indices, added):
        assert subexpr==self._pivot

        isfully = subexpr.get_indexer().get_is_fully_indexed()

        if indices or isfully!=self._is_fully_indexed:
            self._is_fully_indexed = isfully
            self._index_state_changed( indices, added )

    def _do_get_indices(self):
        return [self._pivot] if self._pivot else []

    def _change_and_propagate_index_subscriptions(self, add):
        if not add or self._index_req_cnt:
            self._index_state_changed( self._change_index_subscriptions( add ), add )

    def _change_index_subscriptions(self, add):
        if add and len( self._subexprs ):
            self._pivot = self._subexprs[ 0 ]
            self._is_fully_indexed = self._pivot.get_indexer().get_is_fully_indexed()
            return self._pivot.get_indexer().change_index_subscription( self._principal, True )
        elif not add and self._pivot:
            idxs = self._pivot.get_indexer().change_index_subscription( self._principal, False )
            self._pivot = None
            self._is_fully_indexed = len( self._subexprs )==0
            return idxs
        else:
            return ()

    def _subexpr_changed(self, expr, added):
        if not self._is_active:
            return

        cp = self._subexprs[ 0 ] if len( self._subexprs ) else None

        if cp==self._pivot:
            return

        if self._pivot:
            self._change_and_propagate_index_subscriptions( False )

        if cp:
            self._change_and_propagate_index_subscriptions( True )


class ChoiceIndexer(GroupIndexer, Container):
    def __init__(self, principal, parser):
        super().__init__( principal, parser )

        self._indexed = {}
        self._noindex = []

    def __contains__(self, value):
        return value in self._indexed

    def __getitem__(self, value):
        return self._indexed[ value ]

    def get(self, value, default=Value.Undefined):
        return self._indexed.get( value ) if default==Value.Undefined else self._indexed.get( value, default )

    def get_not_fully_indexed(self):
        return self._noindex

    def get_is_fully_indexed(self):
        return self._is_active and len( self._noindex )==0

    def propagate_indices(self, subexpr, indices, added):
        isfully = self.get_is_fully_indexed()

        if subexpr.get_indexer().get_is_fully_indexed():
            if subexpr in self._noindex:
                self._noindex.remove( subexpr )
        else:
            if subexpr not in self._noindex:
                # We have to maintain the order ... do we? Choices are implemented without relying on that
                # list and unbounds don't have to adhere to an order.
                self._noindex = [s for s in self._subexprs if not s.get_indexer().get_is_fully_indexed()]

        self._change_expr_indices( subexpr, indices, added )

        if indices or isfully!=self.get_is_fully_indexed():
            self._index_state_changed( indices, added )

    def _do_get_indices(self):
        return self._indexed.keys()

    def _subexpr_changed(self, expr, added):
        if not self._is_active:
            return

        idxs = expr.get_indexer().change_index_subscription( self._principal, added )

        isfully = self.get_is_fully_indexed()

        if added and not expr.get_indexer().get_is_fully_indexed():
            self._noindex.append( expr )
        elif not added and expr in self._noindex:
            self._noindex.remove( expr )

        self._change_expr_indices( expr, idxs, added )

        if idxs or isfully!=self.get_is_fully_indexed():
            self._index_state_changed( idxs, added )

    def _change_expr_indices(self, expr, indices, added):
        if added:
            for idx in indices:
                assert idx not in self._indexed
                self._indexed[ idx ] = expr
        else:
            for idx in indices:
                del self._indexed[ idx ]

    def _change_index_subscriptions(self, add):
        self._indexed.clear()
        self._noindex.clear()

        idxs = []
        for expr in self._subexprs:
            for idx in expr.get_indexer().change_index_subscription( self._principal, add ):
                if add:
                    assert idx not in self._indexed
                    self._indexed[ idx ] = expr
                idxs.append( idx )

            if add and not expr.get_indexer().get_is_fully_indexed():
                self._noindex.append( expr )

        return idxs


class ScopeIndexer(ChoiceIndexer):
    def __init__(self, principal):
        super().__init__( principal, None )

        self._unbounds = []

    def subexpression_added(self, expr):
        self._unbounds.append( expr )
        super().subexpression_added( expr )

    def subexpression_removed(self, expr):
        self._unbounds.remove( expr )
        super().subexpression_removed( expr )

    def _subexpr(self):
        return self._unbounds
