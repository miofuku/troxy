#!/usr/bin/env python3.4

from _collections import deque
from abc import ABCMeta, abstractmethod
from collections import namedtuple
from copy import deepcopy
from io import StringIO
import unittest

from translt.data import KeyValueFormat, YAML, CSV, JSON
from translt.fieldsets import RecordFieldSet


# TODO: By now, Translt seems to be complex enough to let real unit tests appear to be more appropriate.
#       For instance: not present or empty collections, objects with more attributes/collections with more
#       elements than specified, unexpected inputs (objects in the case of writing, data in the case of
#       reading), wrong mappings, etc.
class StandardTest:
    class Base(unittest.TestCase, metaclass=ABCMeta):
        def test_kv(self):
            self._do_test( KeyValueFormat() )

        def test_csv(self):
            self._do_test( CSV() )

        def test_yaml(self):
            self._do_test( YAML() )

        def test_json(self):
            self._do_test( JSON() )

        def _do_test(self, datfmt):
            recmap = datfmt.record_mapping( self.field )

            outstream = StringIO()
            datfmt.write_object( self.input_object, outstream, recmap )

            instream  = StringIO( outstream.getvalue() )
            obj = datfmt.read_object( instream, recmap )

            self.check( obj )

        @property
        def field(self):
            raise NotImplementedError()

        @property
        def input_object(self):
            raise NotImplementedError()

        @abstractmethod
        def check(self, obj):
            ...



#@unittest.skip
class SequenceTest(StandardTest.Base):
    @classmethod
    def setUpClass(cls):
        cls.field = RecordFieldSet.create_namedsequence() \
                                    .start_namedsequence() \
                                        .type( deque ) \
                                        .elements_namepattern( 'hello{fieldsep}{}' ) \
                                        .integer() \
                                        .indexoffset( 5 ) \
                                        .start_integer( 1 ).name( 'test' ).end() \
                                        .end()
        cls.field.integer( 1 )
        cls.field.start_namedsequence( 3 ) \
                .integer() \
                .max_count( 2 ) \
                .integrate() \
                .elementname( 0, 'hello' ) \
                .elementname( 1, 'world' )
        cls.field.start_sequence( 4 ) \
                .integer() \
                .max_count( 3 ) \
                .name( 'list' )

        cls.input_object = [[23, 42, 104], 1, [101, 102], [201, 202], [301, 302, 303]]

    def check(self, obj):
        for i, e in enumerate( self.input_object ):
            if isinstance( e, int ):
                self.assertEqual( obj[ i ], e )
            else:
                self.assertSequenceEqual( obj[ i ], e )



#@unittest.skip
class GenericNamedtupleTest(StandardTest.Base):
    @classmethod
    def setUpClass(cls):
        cls.field = RecordFieldSet.create_genericnamedtuple() \
                                        .typename( 'Test' ) \
                                        .integer( 'hello', 'world' )

        cls.input_object = namedtuple( 'Test', 'hello world' )( 23, 42 )

    def check(self, obj):
        self.assertEqual( obj, self.input_object )



#@unittest.skip
class UpdateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dctfld = RecordFieldSet.create_dictionary() \
                                        .start_namedsequence( *[str( i ) for i in range( 5 )] ) \
                                            .update() \
                                            .indexoffset( 1 ) \
                                            .max_count( 2 ) \
                                            .elements_namepattern( 'item{}' ) \
                                            .integer() \
                                            .end()
        cls.dct = { str( i ): [i, -i] for i in range( 5 ) }

        cls.dctfld_diff = RecordFieldSet.create_dictionary() \
                                            .start_dictionary( *[str( i ) for i in range( 5 )] ) \
                                                .update() \
                                                .integer( 'item1', 'item2' ) \
                                                .end()
        cls.dct_diff = { '2': { 'item1': 23 }, '4': { 'item2': 42 } }

        cls.dct_after = deepcopy( cls.dct )
        cls.dct_after[ '2' ][ 0 ] = 23
        cls.dct_after[ '4' ][ 1 ] = 42


    def test_kv(self):
        self._do_test( KeyValueFormat() )

    def test_csv(self):
        self._do_test( CSV() )

    def test_yaml(self):
        self._do_test( YAML() )

    def test_json(self):
        self._do_test( JSON() )

    def _do_test(self, datfmt):
        recdiff = datfmt.record_mapping( self.dctfld_diff )

        outstream = StringIO()
        datfmt.write_object( self.dct_diff, outstream, recdiff )

        recmap = datfmt.record_mapping( self.dctfld )
        instream = StringIO( outstream.getvalue() )
        obj = datfmt.read_object( instream, recmap, deepcopy( self.dct ) )

        self.assertDictEqual( obj, self.dct_after )


@unittest.skip
class PerformanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dct    = {}
        cls.dctfld = RecordFieldSet.create_dictionary()
        for i in range( 1000 ):
            cls.dctfld.string( str( i ) )
            cls.dct[ str( i ) ] = str( i )

#0.945s -> 1.110 (updates)
#    @unittest.skip
    def test_kv(self):
        self._do_test( KeyValueFormat() )

    @unittest.skip
    def test_csv(self):
        self._do_test( CSV() )

#3.865s -> 4.130 (updates)
    @unittest.skip
    def test_yaml(self):
        self._do_test( YAML() )

    @unittest.skip
    def test_json(self):
        self._do_test( JSON() )

    def _do_test(self, datfmt):
        recmap = datfmt.record_mapping( self.dctfld )

        outstream = StringIO()
        datfmt.write_object( self.dct, outstream, recmap )

        instream  = StringIO( outstream.getvalue() )
        obj = datfmt.read_object( instream, recmap )

        self.assertDictEqual( obj, self.dct )
