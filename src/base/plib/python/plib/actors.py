#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod


class Actor(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def receive_message(self, msg):
        ...

    @abstractmethod
    def process_messages(self):
        ...


class Message(metaclass=ABCMeta):
    __slots__ = ()

    @property
    def type(self):
        return type( self )


class MessageDispatcher(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def lock(self):
        ...

    @abstractmethod
    def unlock(self):
        ...

    @abstractmethod
    def send_message(self, msg, actor):
        ...

    @abstractmethod
    def multicast_message(self, msg, actors):
        ...

    @abstractmethod
    def activate_actor(self, actor):
        ...

    @abstractmethod
    def activate_actors(self, actors):
        ...


class QueuedActor(Actor):
    def __init__(self):
        self._msgs = []

    def receive_message(self, msg):
        self._msgs.append( msg )

    def process_messages(self):
        while self._msgs:
            self._process_message( self._msgs.pop() )

    @abstractmethod
    def _process_message(self, msg):
        ...


class SetDispatcher(MessageDispatcher):
    def __init__(self):
        self._proccnt      = 0
        self._ready_actors = set()

    def lock(self):
        self._proccnt +=1

    def unlock(self):
        self._proccnt -=1
        self._process_actors()

    def send_message(self, msg, actor):
        self.multicast_message( msg, (actor,) )

    def multicast_message(self, msg, actors):
        for actor in actors:
            actor.receive_message( msg )
        self.activate_actors( actors )

    def activate_actor(self, actor):
        self.activate_actors( (actor,) )

    def activate_actors(self, actors):
        self._ready_actors.update( actors )
        self._process_actors()

    def deactivate_actor(self, actor):
        self._ready_actors.discard( actor )

    def deactivate_actors(self, actors):
        for actor in actors:
            self.deactivate_actor( actor )

    def _process_actors(self):
        if self._proccnt:
            return

        self._proccnt += 1

        while self._ready_actors:
            actor = self._ready_actors.pop()
            actor.process_messages()

        self._proccnt -= 1
