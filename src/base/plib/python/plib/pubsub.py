#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from collections import namedtuple

from plib.actors import Message
from plib.utils.builders import FluidMixin


class EventBroker(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def add_subscription(self, sub):
        ...

    @abstractmethod
    def remove_subscription(self, sub):
        ...

    @abstractmethod
    def publish(self, event):
        ...


class Event(metaclass=ABCMeta):
    __slots__ = ()

    @property
    def type(self):
        return type( self )


class EventFilter(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def matches(self, event):
        ...


class EventSubscription(EventFilter):
    __slots__ = ()

    @property
    @abstractmethod
    def subscriber(self):
        ...


class EventOccurred(namedtuple( 'EventOccured', 'subscription event' ), Message):
    __slots__ = ()


class EventTypeFilter(EventFilter):
    def __init__(self, evttype):
        self.eventtype = evttype

    def matches(self, event):
        return issubclass( event.type, self.eventtype )


class EventTypeSubscription(EventTypeFilter):
    def __init__(self, subscriber, evttype):
        super().__init__( evttype )
        self._subscriber = subscriber

    @property
    def subscriber(self):
        return self._subscriber


class FilterChainSubscription(EventSubscription, FluidMixin):
    def __init__(self, subscriber):
        FluidMixin.__init__( self )

        self._subscriber = subscriber

        self.filters = []

    def add_filter(self, *filters):
        return self.extend_filters( filters )

    def extend_filters(self, filters):
        self.filters.extend( filters )
        return self

    def match_type(self, evttype):
        return self.add_filter( EventTypeFilter( evttype ) )

    def matches(self, event):
        for fil in self.filters:
            if not fil.matches( event ):
                return False
        return True

    @property
    def subscriber(self):
        return self._subscriber


# TODO: Provide version that uses (generic) indices.
class SubscriptionListBroker(EventBroker):
    def __init__(self, dispatcher):
        self.subscriptions = []
        self.dispatcher    = dispatcher

    def add_subscription(self, sub):
        self.subscriptions.append( sub )

    def remove_subscription(self, sub):
        self.subscriptions.remove( sub )

    def publish(self, event):
        for sub in self.subscriptions:
            if sub.matches( event ):
                self.dispatcher.send_message( EventOccurred( sub, event ), sub.subscriber )


class SubscriptionRegistry(EventBroker):
    def __init__(self, broker):
        self.broker        = broker
        self.subscriptions = {}

    def add_subscription(self, sub):
        lst = self.subscriptions.get( sub.subscriber )

        if lst is None:
            lst = self.subscriptions[ sub.subscriber ] = []

        lst.append( sub )
        self.broker.add_subscription( sub )

    def remove_subscription(self, sub):
        self.broker.remove_subscription( sub )
        self.subscriptions[ sub.subscriber ].remove( sub )

    def remove_subscriber(self, subscriber):
        for sub in self.subscriptions[ subscriber ]:
            self.broker.remove_subscription( sub )
        del self.subscriptions[ subscriber ]

    def discard_subscriber(self, subscriber):
        if subscriber in self.subscriptions:
            self.remove_subscriber( subscriber )

    def publish(self, event):
        self.broker.publish( event )
