#!/usr/bin/env python3.4


class Fieldnames:
    Integrated = None
    Plain      = None

    __slots__ = ()

    def is_integrated(self, index):
        return False

    def for_index(self, index, cntxt, idxproc=None):
        raise NotImplementedError()

    def guess_index(self, name):
        raise NotImplementedError()


class __IntegratedFieldnames(Fieldnames):
    __slots__ = ()

    def is_integrated(self, index):
        return True

Fieldnames.Integrated = __IntegratedFieldnames()


class __PlainFieldnames(Fieldnames):
    __slots__ = ()

    def for_index(self, index, cntxt, idxproc=None):
        return index

    def guess_index(self, name):
        return name

Fieldnames.Plain = __PlainFieldnames()



class FieldnamesBase(Fieldnames):
    __slots__ = ()

    @classmethod
    def get(cls, value, inverse=None):
        if value is None:
            return Fieldnames.Plain
        elif isinstance( value, str ):
            return cls( value, inverse )
        else:
            return value


class SingleFieldname(FieldnamesBase):
    __slots__ = 'name', 'index'

    def __init__(self, name, index=None):
        self.name  = name
        self.index = index

    def for_index(self, index, cntxt, idxproc=None):
        return self.name

    def guess_index(self, name):
        return self.index if name==self.name else None


class PatternedFieldnames(FieldnamesBase):
    __slots__ = 'pattern', 'inverse'

    def __init__(self, pattern, inverse=None):
        self.pattern = pattern
        self.inverse = inverse

    def is_integrated(self, index):
        return self.pattern is None

    def for_index(self, index, cntxt, idxproc=None):
        if idxproc is not None:
            index = idxproc( index )

        return self.pattern.format( index, cntxt=cntxt, fieldsep=cntxt.mapping.format.fieldsep )

    def guess_index(self, name):
        return self.inverse( name ) if self.inverse is not None else None



class FieldnamesCollection(Fieldnames):
    __slots__ = 'default', 'items', 'inverse'

    def __init__(self, default=None, inverse=None):
        self.default = PatternedFieldnames( default if default is not None else '{}', inverse )
        self.items   = {}
        self.inverse = {}

    def set_default_pattern(self, pattern, inverse=None):
        self.default.pattern = pattern
        self.default.inverse = inverse

    def __getitem__(self, index):
        return self.items.get( index, None ) or self.default

    def is_integrated(self, index):
        return self[ index ].is_integrated( index )

    def for_index(self, index, cntxt, idxproc=None):
        return self[ index ].for_index( index, cntxt, idxproc )

    def guess_index(self, name):
        return self.inverse.get( name, self.default ).guess_index( name )

    def __setitem__(self, index, value):
        if value is None:
            self.items[ index ] = Fieldnames.Integrated
        else:
            item = SingleFieldname.get( value, index )

            self.items[ index ]   = item
            self.inverse[ value ] = item

    def set(self, index, value):
        assert value is not None, 'What is this for?'
        if value is not None:
            self[ index ] = value

    @classmethod
    def get(cls, value):
        return value if value is not None and isinstance( value, FieldnamesCollection ) else cls( value )
