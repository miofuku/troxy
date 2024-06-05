#!/usr/bin/env python3.4

from _collections_abc import Iterator
from abc import abstractmethod
from collections import OrderedDict
import csv
import json
import re

from translt import Format, ObjectReader, ObjectWriter
from translt.flat import FlatMapping
from translt.hier import HierMapping
import yaml
from yaml.dumper import Dumper
from yaml.events import ScalarEvent
from yaml.nodes import ScalarNode


class DataFormat(Format):
    @property
    @abstractmethod
    def record_class(self):
        ...

    @property
    def is_hier(self):
        return self.record_class is HierMapping

    @property
    def is_flat(self):
        return self.record_class is FlatMapping

    @property
    @abstractmethod
    def default_file_extension(self):
        ...

    @abstractmethod
    def record_mapping(self, field, env=None):
        ...

    @abstractmethod
    def object_reader(self, stream, recmap):
        ...

    def reader_for_field(self, stream, field, env=None):
        return self.object_reader( stream, self.record_mapping( field, env ) )

    def read(self, stream, recmap):
        reader = self.object_reader( stream, recmap )
        objs   = []

        for obj in reader:
            objs.append( obj )

        return objs

    def read_object(self, stream, recmap, obj=None):
        return self.object_reader( stream, recmap ).read_object( obj )

    @abstractmethod
    def object_writer(self, stream, recmap):
        ...

    def writer_for_field(self, stream, field, env=None):
        return self.object_writer( stream, self.record_mapping( field, env ) )

    def write(self, objs, stream, recmap):
        writer = self.object_writer( stream, recmap )

        for obj in objs:
            writer.write_object( obj )

    def write_object(self, obj, stream, recmap):
        self.object_writer( stream, recmap ).write_object( obj )



class FlatReader(Iterator):
    pass


class FlatDataFormat(DataFormat):
    record_class = FlatMapping

    @abstractmethod
    def reader(self, stream):
        ...

    def object_reader(self, stream, recmap):
        return DictObjectReader( self.reader( stream ), recmap )

    def record_mapping(self, field, env=None, fmt=None):
        return FlatMapping( field, env, fmt )

    def reader_for_field(self, stream, field, env=None, fmt=None ):
        return self.object_reader( stream, self.record_mapping( field, env, fmt ) )

    def writer_for_field(self, stream, field, env=None, fmt=None):
        return self.object_writer( stream, self.record_mapping( field, env, fmt ) )



class HierDataFormat(DataFormat):
    record_class = HierMapping

    def record_mapping(self, field, env=None, fmt=None):
        return HierMapping( field, env, fmt )



class DictObjectReader(ObjectReader):
    def __init__(self, reader, recmap):
        self.reader = reader
        self.recmap = recmap

    def read_object(self, obj=None):
        data = next( self.reader )
        return self.recmap.read_from( data, obj )



class CSVObjectWriter(ObjectWriter):
    def __init__(self, stream, recmap, colnames, auto_header=True, **writerargs):
        self.stream      = stream
        self.count       = 0
        self.recmap      = recmap
        self.colnames    = colnames
        self.auto_header = auto_header
        self.writerargs  = writerargs
        self.writer      = None

    def _create_writer(self, obj=None):
        if self.colnames is None:
            self.colnames = self.recmap.get_column_names( obj ).names_only()

        self.writer = csv.DictWriter( self.stream, self.colnames, **self.writerargs )

    def write_header(self, obj=None):
        if not self.writer:
            self._create_writer( obj )

        self.writer.writeheader()

    def write_object(self, obj):
        if not self.writer:
            self._create_writer( obj )

        if not self.count and self.auto_header:
            self.writer.writeheader()

        data = {}
        self.recmap.write_to( data, obj )
        self.writer.writerow( data )

        self.count += 1


class CSV(FlatDataFormat):
    def __init__(self, delimiter=';'):
        self.delimiter = delimiter

    @property
    def default_file_extension(self):
        return 'csv'

    def reader(self, stream):
        return csv.DictReader( stream, delimiter=self.delimiter )

    def object_writer(self, stream, recmap, colnames=None, auto_header=True):
        return CSVObjectWriter( stream, recmap, colnames, auto_header, extrasaction='ignore', delimiter=self.delimiter )

    def write(self, objs, stream, recmap):
        # objs can be a generator...
#        if len( objs )==0:
#            return

        # TODO: Implement some kind of optional merge.
        #       1: a_3
        #       2: a_1
        #       3: a_2 a_1
        #       -> a_2 a_1 a_3 ...
#        combined = recmap.get_column_names( objs[ 0 ] )

        writer = self.object_writer( stream, recmap )

        for obj in objs:
            writer.write_object( obj )


class KeyValueReader(FlatReader):
    def __init__(self, stream, fmt):
        self.stream = stream
        self.format = fmt

    def __next__(self):
        data = self.format.datacont()

        for line in self.stream:
            if self.format.objsep is not None and line==self.format.objsep:
                break
            else:
                line = line.strip()

                if line and ( self.format.comment is None or not line.startswith( self.format.comment ) ):
                    try:
                        k, v = line.split( '=', 2 )
                    except ValueError:
                        raise ValueError( "Invalid line: '{}'".format( line ) )
                    data[ k.strip() ] = v.strip()

        if data:
            return data
        else:
            raise StopIteration()



class KeyValueObjectWriter(ObjectWriter):
    def __init__(self, stream, recmap, fmt):
        self.stream = stream
        self.recmap = recmap
        self.format = fmt
        self.count = 0

    def write_object(self, obj):
        if self.count:
            if self.format.objsep is not None:
                self.stream.write( self.format.objsep )
            else:
                raise EOFError( 'Writing multiple objects is unsupported when no separator is given.' )

        data = OrderedDict()
        self.recmap.write_to( data, obj )

        if data:
            l = len( max( data.keys(), key=lambda x: len(x) ) )

        for k, v in data.items():
            if v is None: v = ''
            self.stream.write( '{key:{width}} = {value}\n'.format( key=k, value=v, width=l ))

        self.count += 1



class KeyValueFormat(FlatDataFormat):
    def __init__(self, objsep=None, comment='#', datacont=dict):
        self.objsep   = objsep
        self.comment  = comment
        self.datacont = datacont

    @property
    def default_file_extension(self):
        return 'kv'

    def reader(self, stream):
        return KeyValueReader( stream, self )

    def object_writer(self, stream, recmap):
        return KeyValueObjectWriter( stream, recmap, self )



class ExtendedYAMLDumper(Dumper):
    def __init__(self, *args, align_block_tags=False, **kwargs):
        super().__init__( *args, **kwargs )

        self.align_block_tags = align_block_tags

        self.add_representer( OrderedDict, ExtendedYAMLDumper.represent_ordereddict )


    def represent_ordereddict(self, data):
        value = []
        node  = yaml.nodes.MappingNode( 'tag:yaml.org,2002:map', value )

        if self.alias_key is not None:
            self.represented_objects[ self.alias_key ] = node

        if self.align_block_tags and data:
            block_width = max( [len( str(key) ) for key in data.keys()] ) + 1

        for item_key, item_value in data.items():
            node_key   = self.represent_data( item_key )
            node_value = self.represent_data( item_value )

            if self.align_block_tags and isinstance( node_key, ScalarNode ) and \
                    node_key.tag=='tag:yaml.org,2002:str' and node_key.style is None:
                node_key.block_width = block_width

            value.append( (node_key, node_value) )

        return node


    def serialize_node(self, node, parent, index):
        if isinstance( node, ScalarNode ) and hasattr( node, 'block_width' ) and node not in self.serialized_nodes:
            alias = self.anchors[node]

            self.serialized_nodes[node] = True
            self.descend_resolver( parent, index )

            detected_tag = self.resolve( ScalarNode, node.value, (True, False) )
            default_tag  = self.resolve( ScalarNode, node.value, (False, True) )
            implicit     = (node.tag == detected_tag), (node.tag == default_tag)
            event        = ScalarEvent(alias, node.tag, implicit, node.value, style=node.style)
            event.block_width = node.block_width

            self.emit( event )

            self.ascend_resolver()
        else:
            super().serialize_node( node, parent, index )


    def process_scalar(self):
        if self.style=='' and hasattr( self.event, 'block_width' ):
            padding = self.event.block_width - len( self.analysis.scalar )

            if padding>0:
                self.analysis.scalar += ' '*padding

        super().process_scalar()


# TODO: Add close() and turn reader and writer into context manager.
class YAMLObjectWriter(ObjectWriter):
    def __init__(self, stream, recmap, align_block_tags=False):
        self.stream = stream
        self.recmap = recmap
        self.count  = 0
        self.align_block_tags = align_block_tags

    def write_object(self, obj):
        data = []
        self.recmap.write_to( data, obj )

        dumper = ExtendedYAMLDumper( stream=self.stream, explicit_start=self.count>0, align_block_tags=self.align_block_tags )
        try:
            dumper.open()
            dumper.represent( data[ 0 ] )
            dumper.close()
        finally:
            dumper.dispose()

        self.count += 1



# TODO: Reader should be (optionally) able to preserve the item order by returning OrderedDict instead of dict.
class YAMLObjectReader(ObjectReader):
    def __init__(self, reader, recmap):
        self.reader = reader
        self.recmap = recmap

    def read_object(self, obj=None):
        data = next( self.reader )
        return self.recmap.read_from( data, obj )



class YAML(HierDataFormat):
    def __init__(self, align_block_tags=True):
        self.align_block_tags = align_block_tags

    @property
    def default_file_extension(self):
        return 'yaml'

    def object_reader(self, stream, recmap):
        return YAMLObjectReader( yaml.load_all( stream ), recmap )

    def object_writer(self, stream, recmap):
        return YAMLObjectWriter( stream, recmap, self.align_block_tags )



class JSONObjectWriter(ObjectWriter):
    def __init__(self, stream, recmap, indent=None, separators=None):
        self.stream     = stream
        self.recmap     = recmap
        self.indent     = indent
        self.separators = separators

    def write_object(self, obj):
        data = []
        self.recmap.write_to( data, obj )

        json.dump( data[ 0 ], self.stream, indent=self.indent, separators=self.separators )

        self.stream.write( '\n' )



class JSONObjectReader(ObjectReader):
    class JSONObjectsDecoder(json.JSONDecoder):
        _ws = re.compile( r'\s*' )

        def __init__(self, *args, reader=None, **kwargs):
            super().__init__( *args, **kwargs )

            self.reader = reader

        def decode(self, s):
            data, pos = self.raw_decode( s, idx=self._ws.match( s, self.reader.curpos ).end() )

            self.reader.curpos = self._ws.match( s, pos ).end()

            return data

    def __init__(self, stream, recmap):
        self.datastr = stream.read()
        self.recmap  = recmap
        self.curpos  = 0

    def read_object(self, obj=None):
        if self.curpos==len(self.datastr):
            raise StopIteration()

        data = json.loads( self.datastr, cls=self.JSONObjectsDecoder, reader=self )
        return self.recmap.read_from( data, obj )



class JSON(HierDataFormat):
    def __init__(self, indent=2, separators=(',', ': ')):
        self.indent     = indent
        self.separators = separators

    @property
    def default_file_extension(self):
        return 'json'

    def object_reader(self, stream, recmap):
        return JSONObjectReader( stream, recmap )

    def object_writer(self, stream, recmap):
        return JSONObjectWriter( stream, recmap, self.indent, self.separators )
