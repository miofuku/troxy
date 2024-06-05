#!/usr/bin/env python3

# Nomenclature for builder methods:
#   get_       -> value
#   start_     creates and adds/sets subbuilder -> subbuilder
#   <type>     creates and adds/sets subbuilder -> self
#   init_      adds/sets given subbuilder -> subbuilder
#   set_       sets given subbuilder -> self
#   add_       adds given subbuilder(s) -> self
#   extend_    adds given subbuilders -> self
#   configure_ -> existing subbuilder

class FluidMixin:
    __slots__ = '_caller'

    def __init__(self):
        super().__init__()
        self._caller  = None

    def end(self):
        assert self._caller
        return self._caller

    def caller(self, caller):
        self._caller = caller
        return self

    def get_caller(self):
        return self._caller


class FluidBuilderMixin(FluidMixin):
    __slots__ = '_config'

    def __init__(self, config):
        FluidMixin.__init__( self )

        self._config = config

    def get_config(self):
        return self._config



class Reference:
    instance = None

    __slots__ = 'target'

    def __init__(self, target=None):
        self.target = target

    @classmethod
    def _inst(cls, obj):
        if isinstance( obj, Reference ):
            return obj
        elif isinstance( obj, type ) and issubclass( obj, Reference ):
            return obj.instance
        else:
            return None

    @classmethod
    def dest(cls, dest, default):
        if dest is None:
            return default

        inst = cls._inst( dest )

        if inst is None:
            return dest
        else:
            inst.target = default
            return default

    @classmethod
    def source(cls, src):
        inst = cls._inst( src )

        return inst.target if inst else src

Reference.instance = Reference()
