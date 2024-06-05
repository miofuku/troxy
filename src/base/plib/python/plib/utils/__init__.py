#!/usr/bin/env python3.4

def callable_singleton(name, base, call, scope=None):
    """Creates a single instance of a dynamically generated, callable type.

    Example:
        ::

            class PathProcessor(ABC):
                @abstractmethod
                def __call__(self, path):
                    ...

                Empty  = None
                IsFile = None

            PathProcessor.Empty  = callable_singleton( 'Empty', PathProcessor, lambda self, path: path )
            PathProcessor.IsFile = callable_singleton( 'IsFile', PathProcessor,
                                                       lambda self, path: path if os.path.isfile( path ) else None )

    Args:
        name (str): The type name of the dynamically created type.
        base (type): The base of the created type or None.
        call (func): The callable function.
        scope (str or type, optional): A string or type that is used instead of `base`
            as scope of the created type.

    Note:
        Either `base` or `scope` must be given.

    Returns:
        Callable singleton.

    """
    assert base is not None or scope is not None

    if scope is None:
        scope = base

    if isinstance( scope, str ):
        repr_ = '.'.join( (scope, name) )
    else:
        repr_ = '.'.join( (base.__module__, base.__qualname__, name) )

    str_  = name
    attrs = { '__call__': call, '__str__': lambda self: str_, '__repr__': lambda self: repr_ }

    return type( name, (base,), attrs )()



class _Undefined:
    __slots__ = ()

    def __repr__(self):
        return 'Undefined'

class Value:
    __slots__ = ()

    Undefined = _Undefined()
