#!/usr/bin/env python3.4

from abc import ABC, abstractmethod
import unittest

from plib.utils import callable_singleton


class CallableSingletonTest(unittest.TestCase):
    class PathProcessor(ABC):
        @abstractmethod
        def __call__(self, path):
            ...

        Empty = None

    PathProcessor.Empty = callable_singleton( 'Empty', PathProcessor, lambda self, path: path )
    GlobalEmpty = callable_singleton( 'GlobalEmpty', PathProcessor, lambda self, path: path, __name__ )

    def test_function(self):
        self.assertEqual( CallableSingletonTest.PathProcessor.Empty( 'path' ), 'path' )

    def test_str(self):
        self.assertEqual( str( CallableSingletonTest.PathProcessor.Empty ), 'Empty' )

    def test_repr(self):
        self.assertEqual( repr( CallableSingletonTest.PathProcessor.Empty ),
                          __name__ + '.CallableSingletonTest.PathProcessor.Empty' )

    def test_scope(self):
        self.assertEqual( repr( CallableSingletonTest.GlobalEmpty ),  __name__ + '.GlobalEmpty' )


if __name__=="__main__":
    unittest.main()
