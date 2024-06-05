#!/usr/bin/env python3.4

import datetime


def parse_datetime_str(str_):
    if len( str_ )==19:
        fmt = '%Y-%m-%d %H:%M:%S'
    elif len( str_ )==26:
        fmt = '%Y-%m-%d %H:%M:%S.%f'
    elif len( str_ )==32:
        fmt  = '%Y-%m-%d %H:%M:%S.%f%z'
        str_ = str_[ :29 ]+str_[ 30: ]
    else:
        raise ValueError( "Unsupported datetime format '{}'".format( str_ ) )

    return datetime.datetime.strptime( str_, fmt )
