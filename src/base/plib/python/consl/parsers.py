#!/usr/bin/env python3.4

from intrpt.parsers import TerminalParser


class FlagGroupParser(TerminalParser):
    def __init__(self, expr, prefix, config):
        super().__init__( expr, self._create_indexer( expr, config ), config )

        self.prefix = prefix
        self.flags  = {}

    def _create_indexer(self, expr, config):
        return config.create_variable_indexer( expr, self )

    def _parse_value(self, cntxt, value):
        value = super()._parse_value( cntxt, value )

        if not value.startswith( self.prefix ):
            cntxt.on_error( self )
            return None
        else:
            found = {}

            for f in value[ len(self.prefix): ]:
                if f in self.flags:
                    found[ f ] = found.get( f, 0 )+1
                else:
                    cntxt.on_error( self, msg="Unknown flag '{}'".format( f ) )
                    break

            for f, cnt in found.items():
                if not self.flags[ f ].get_unbound_control().is_available( cntxt, cnt ):
                    cntxt.on_error( self.flags[ f ], msg="Too many occurrences of '{}'".format( f ) )
                    break

            return value[ len(self.prefix): ]

    def _on_success(self, cntxt, value):
        super()._on_success( cntxt, value )

        for f in value:
            flag = self.flags[ f ]
            flag.get_unbound_control().decrement_count( cntxt )
            flag.execute_actions( cntxt, self.expr, 1 )


