#!/usr/bin/env python3.4

class AttributeDict(dict):
    def __getattr__(self, name):
        try:
            return super().__getattribute__( name )
        except AttributeError as e:
            if name in self:
                return self[ name ]
            else:
                raise e

    def __setattr__(self, name, value):
        if hasattr( self, name ):
            dict.__setattr__( self, name, value )
        else:
            self[ name ] = value

    def __delattr__(self, name):
        if hasattr( self, name ) or name not in self:
            dict.__delattr__( self, name )
        else:
            del self[ name ]
