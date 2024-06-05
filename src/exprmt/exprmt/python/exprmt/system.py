#!/usr/bin/env python3.4

from abc import abstractmethod
from collections import namedtuple, OrderedDict
from collections.abc import Container, Sized, Iterable

from plib.actors import QueuedActor, SetDispatcher
from plib.pubsub import EventSubscription, Event, EventOccurred, SubscriptionRegistry, SubscriptionListBroker


class SystemSubscription(EventSubscription):
    __slots__ = ()

    def process(self, event):
        self.handler( self, event )

    @property
    @abstractmethod
    def handler(self):
        ...


class EntityTypeSubscription(namedtuple('EntityTypeSubscription', 'subscriber eventtype entitytype handler'), SystemSubscription):
    __slots__ = ()

    def matches(self, event):
        return issubclass( event.type, self.eventtype ) and \
               issubclass( event.entitytype, self.entitytype )


class EntitySubscription(namedtuple('EntitySubscription', 'subscriber eventtype entitytype predicate handler'), SystemSubscription):
    __slots__ = ()

    def matches(self, event):
        return issubclass( event.type, self.eventtype ) and \
               issubclass( event.entity.type, self.entitytype ) and \
               self.predicate( event.entity )


class EntityTypeEvent(Event):
    __slots__ = '_entitytype'

    def __init__(self, enttype):
        self._entitytype = enttype

    # TODO: event.type, entitytype etc. should be generic properties of events. This would allow a generic
    #       indexing and filtering. But what about properties of attached object, e.g. event.entity.host?
    #       translt?
    @property
    def entitytype(self):
        return self._entitytype

    class SpecificFilter(namedtuple( 'SpecificFilter', 'entitytype' )):
        __slots__ = ()

        def matches(self, event):
            return event.entitytype==self.entitytype

    class AbstractFilter(namedtuple( 'AbstractFilter', 'entitytype' )):
        __slots__ = ()

        def matches(self, event):
            return issubclass( event.entitytype, self.entitytype )


class EntityTypeAdded(EntityTypeEvent):
    __slots__ = ()


class EntityEvent(Event):
    __slots__ = '_entity'

    def __init__(self, ent):
        self._entity = ent

    @property
    def entity(self):
        return self._entity

    class SpecificFilter(namedtuple( 'SpecificFilter', 'entitytype' )):
        __slots__ = ()

        def matches(self, event):
            return event.entity.type==self.entitytype

    class AbstractFilter(namedtuple( 'AbstractFilter', 'entitytype' )):
        __slots__ = ()

        def matches(self, event):
            return issubclass( event.entity.type, self.entitytype )


class EntityRegistrationChanged(EntityEvent):
    __slots__ = ()


class EntityRegistered(EntityRegistrationChanged):
    __slots__ = ()


class EntityRemoved(EntityRegistrationChanged):
    __slots__ = ()


class EntityBlocked(Exception):
    def __init__(self, ent, block):
        super().__init__( 'Cannot remove {} (blocked by {})'.format( ent, block ) )
        self.entity = ent
        self.blocking_entity = block


class SystemEntity(QueuedActor):
    category = None

    def __init__(self):
        super().__init__()

        self._system = None

    def _process_message(self, msg):
        assert self._system
        assert issubclass( msg.type, EventOccurred )
        msg.subscription.process( msg.event )

    def _initialize(self, system, cntxt):
        assert self._system is None
        self._system = system

    def _dispose(self, disset):
        assert self._system is not None
        self._msgs.clear()
        self._system = None

    def register(self, system):
        system.register_entity( self )
        return self

    def remove(self):
        self.system.remove_entity( self )

    @property
    def system(self):
        return self._system

    @property
    def type(self):
        return type( self )

    @classmethod
    def basetypes(cls):
        return iter( c for c in cls.mro() if issubclass( c, SystemEntity ) )

    def dependent_entities(self):
        return ()

    def _subscribe_lifecycle(self, enttype, predicate, registered_handler, removed_handler):
        self.system.subscribe_for_event( EntitySubscription( self, EntityRegistered, enttype, predicate, registered_handler ) )
        self.system.subscribe_for_event( EntitySubscription( self, EntityRemoved, enttype, predicate, removed_handler ) )

SystemEntity.category = SystemEntity


class SystemObject(SystemEntity):
    pass


class SystemGroup(SystemEntity, Container, Sized, Iterable):
    @abstractmethod
    def ordered(self):
        ...


class SystemEntityGroup(SystemGroup):
    category = None

SystemEntityGroup.category = SystemEntityGroup


class SystemEntityTypeGroup(SystemGroup):
    category = None

    def ordered(self):
        return iter( self )

SystemEntityTypeGroup.category = SystemEntityTypeGroup


class SubtypesOfType(SystemEntityTypeGroup):
    type = None

    @property
    @abstractmethod
    def entitytype(self):
        ...

SubtypesOfType.type = SubtypesOfType


class EntitiesOfSpecificType(SystemEntityGroup):
    def ordered(self):
        return iter( self )

    @property
    @abstractmethod
    def entitytype(self):
        ...


class AllEntitiesOfSpecificType(EntitiesOfSpecificType):
    type = None

AllEntitiesOfSpecificType.type = AllEntitiesOfSpecificType


class AllEntitiesOfAbstractType(SystemEntityGroup):
    type = None

    @property
    @abstractmethod
    def entitytype(self):
        ...

AllEntitiesOfAbstractType.type = AllEntitiesOfAbstractType


class FilteringSubtypesOfType(SubtypesOfType):
    def __init__(self, enttype):
        super().__init__()

        self._enttype = enttype

    @property
    def entitytype(self):
        return self._enttype

    def __contains__(self, obj):
        return isinstance( obj, type ) and issubclass( obj, self._enttype )

    def __len__(self):
        return len( [t for t in self] )

    def __iter__(self):
        return iter( t for t in self._rawtypes() if issubclass( t, self._enttype ) )

    def _rawtypes(self):
        if self._enttype.category==self._enttype:
            return self.system.all_entitytypes()
        else:
            return self.system.subtypes_of_type( self._enttype.category )


class StoringSubtypesOfType(SubtypesOfType):
    def __init__(self, enttype):
        super().__init__()

        self._enttype  = enttype
        self._subtypes = None

    def _initialize(self, system, cntxt):
        super()._initialize( system, cntxt )

        self._subtypes = [enttype for enttype in self.system.all_entitytypes() if issubclass( enttype, self._enttype )]

        system.subscribe_for_event( EntityTypeSubscription( self, EntityTypeAdded, self._enttype, self._entitytype_added ) )

    def _entitytype_added(self, sub, event):
        self._subtypes.append( event.entitytype )

    @property
    def entitytype(self):
        return self._enttype

    def __contains__(self, obj):
        return isinstance( obj, type ) and issubclass( obj, self._enttype )

    def __len__(self):
        return len( self._subtypes )

    def __iter__(self):
        return iter( self._subtypes )


class GeneratingAllEntitiesOfAbstractType(AllEntitiesOfAbstractType):
    def __init__(self, enttype):
        super().__init__()

        self._enttype = enttype

    def ordered(self):
        return iter( self )

    @property
    def entitytype(self):
        return self._enttype

    def __contains__(self, obj):
        return isinstance( obj, SystemEntity ) and issubclass( obj.type, self._enttype )

    def __len__(self):
        return len( [e for e in self] )

    def __iter__(self):
        return iter( e for enttype in self.system.subtypes_of_type( self._enttype ) for e in self.system.entities_of_specific_type( enttype ) )


class ContainedEntities(SystemEntityGroup):
    def __init__(self, enttype):
        super().__init__()

        self._enttype   = enttype
        self._entities  = {}

    def _initialize(self, system, cntxt):
        super()._initialize( system, cntxt )

        self._subscribe_lifecycle( self._enttype, self._predicate, self._entity_registered, self._entity_removed )

    def _dispose(self, disset):
        self._entities.clear()
        super()._dispose( disset )

    @abstractmethod
    def _predicate(self, x):
        ...

    def _entity_registered(self, sub, event):
        self._add_entity( event.entity )

    def _add_entity(self, ent):
        key = self._key( ent )

        entlist = self._entities.get( key, None )

        if entlist is None:
            entlist = self._entities[ key ] = []

        entlist.append( ent )

    def _entity_removed(self, sub, event):
        self._remove_entity( event.entity )

    def _remove_entity(self, ent):
        key = self._key( ent )

        entlist = self._entities[ key ]
        entlist.remove( ent )

        if not entlist:
            del self._entities[ key ]

    def _key(self, ent):
        return ent.type

    def __contains__(self, x):
        return isinstance( x, self._enttype ) and self._predicate( x )

    def __len__(self):
        return sum( len( ents ) for ents in self._entities.values() )

    def _entitytypes(self):
        if len( self._entities )==1:
            return self._entities.keys()
        else:
            return [enttype for enttype in self.system.subtypes_of_type( self._enttype ) if enttype in self._entities]

    def ordered(self):
        for enttype in self._entitytypes():
            yield from self._entities[ enttype ]

    def __iter__(self):
        for values in self._entities.values():
            yield from values


class AdjustableEntityGroup(SystemEntityGroup):
    def __init__(self):
        super().__init__()

        self._entities = []

    @abstractmethod
    def _create_entity(self, i):
        ...

    def set_count(self, cnt):
        assert self._system
        cntdiff = cnt-len( self )
        if cntdiff>0:
            for i in range( len( self ), cnt ):
                ent = self._create_entity( i )
                self._entities.append( ent )
                ent.register( self._system )
        elif cntdiff<0:
            for ent in reversed( self._entities[ cnt: ] ):
                ent.remove()

    def _dispose_entity(self, ent, disset):
        if self not in disset:
            i = self._entities.index( ent )
            for b in reversed( self._entities[ i+1: ] ):
                if b not in disset:
                    raise EntityBlocked( ent, b )

        self._entities.remove( ent )

    def __getitem__(self, number):
        return self._entities[ number ]

    def __contains__(self, x):
        return x in self._entities

    def __len__(self):
        return len( self._entities )

    def ordered(self):
        yield from self._entities

    def __iter__(self):
        yield from self._entities

    def dependent_entities(self):
        return self._entities


# TODO: System(Fluid) -> SystemEntityController and SystemSettings?
class System(SystemEntity):
    category = None

    def __init__(self):
        super().__init__()

        self._dispatcher  = SetDispatcher()
        self._eventbroker = SubscriptionRegistry( SubscriptionListBroker( self._dispatcher ) )
        self._entities    = OrderedDict()
        self._groups      = {}

        self._bootstrap_groups()
        self._init_default_entitytypes()

    class _AllTypes(SubtypesOfType):
        entitytype = SystemEntity

        def _dispose(self, disset):
            raise NotImplementedError( 'This group is not removable.' )

        def __contains__(self, obj):
            return obj in self.system._entities

        def __len__(self):
            return len( self.system._entities )

        def __iter__(self):
            return iter( self.system._entities.keys() )

    class _InnerAllEntitiesOfSpecificType(AllEntitiesOfSpecificType):
        def __init__(self, enttype):
            super().__init__()

            self._entitytype = enttype

        @property
        def entitytype(self):
            return self._entitytype

        def __contains__(self, obj):
            return obj.type==self.entitytype

        def __len__(self):
            return len( self.system._entities[ self.entitytype ] )

        def __iter__(self):
            return iter( self.system._entities[ self.entitytype ] )

    def _bootstrap_groups(self):
        self.add_entitytype( SubtypesOfType )
        self._groups[ SubtypesOfType ] = { SystemEntity: self._AllTypes().register( self ) }

    def _init_default_entitytypes(self):
        self.add_entitytype( AllEntitiesOfSpecificType, AllEntitiesOfAbstractType )

    def add_entitytype(self, *enttypes):
        for enttype in enttypes:
            self._entities[ enttype ] = []
            self._on_entitytype_added( enttype, EntityTypeAdded )

    def _on_entitytype_added(self, enttype, evttype):
        self._eventbroker.publish( evttype( enttype ) )

    def activate_entity(self, ent):
        self._dispatcher.activate_actor( ent )

    def register_entity(self, ent):
        self._dispatcher.lock()

        entlist = self._entities[ ent.type ]
        entlist.append( ent )
        ent._initialize( self, None )
        self._on_entitystate_changed( ent, EntityRegistered )

        self._dispatcher.unlock()

    def remove_entity(self, ent):
        self._dispatcher.lock()

        disset = self._dependency_closure( ent )

        for dis in disset:
            self._remove_single_entity( dis, disset )

        self._dispatcher.unlock()

    def _remove_single_entity(self, ent, disset):
        ent._dispose( disset )
        self._dispatcher.deactivate_actor( ent )
        self._eventbroker.discard_subscriber( ent )
        self._entities[ ent.type ].remove( ent )
        self._on_entitystate_changed( ent, EntityRemoved )

    def _dependency_closure(self, ent):
        closure = set()
        deps    = [ent]

        while deps:
            e = deps.pop()
            if e not in closure:
                closure.add( e )
                deps.extend( e.dependent_entities() )

        return closure

    def _on_entitystate_changed(self, ent, evttype):
        self._eventbroker.publish( evttype( ent ) )

    def _adjust_entity_count(self, enttype, cnt, factory):
        cntdiff = cnt-len( self._entities[ enttype ] )
        if cntdiff>0:
            for _ in range( cntdiff ):
                factory( enttype ).register( self )
        else:
            for ent in reversed( self._entities[ enttype ][ cnt: ] ):
                ent.remove()

    def all_entitytypes(self):
        return self._groups[ SubtypesOfType ][ SystemEntity ]

    def subtypes_of_type(self, enttype):
        grptype = StoringSubtypesOfType if enttype==enttype.category else FilteringSubtypesOfType
        return self._innergroup( SubtypesOfType, grptype, enttype )

    def entities_of_specific_type(self, enttype):
        # The group EntitiesOfSpecificType contains itself. Not sure if that should really the case. Remember Frege!
        # However, compare object and Collection(object).
        return self._innergroup( AllEntitiesOfSpecificType, self._InnerAllEntitiesOfSpecificType, enttype )

    def entities_of_abstract_type(self, enttype):
        return self._innergroup( AllEntitiesOfAbstractType, GeneratingAllEntitiesOfAbstractType, enttype )

    def _innergroup(self, grptype, factory, enttype):
        grpdct = self._groups.get( grptype, None )

        if grpdct is None:
            grpdct = self._groups[ grptype ] = {}

        group = grpdct.get( enttype, None )

        if group is None:
            group = grpdct[ enttype ] \
                  = factory( enttype ).register( self )

        return group

    def subscribe_for_event(self, sub):
        self._eventbroker.add_subscription( sub )

    def remove_subscription(self, sub):
        self._eventbroker.remove_subscription( sub )

System.category = System
