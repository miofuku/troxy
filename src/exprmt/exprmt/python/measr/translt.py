#!/usr/bin/env python3.4

from abc import abstractmethod
from enum import IntEnum

from measr import StatisticalAccumulator, FrequencySink, IntervalResult, DurationResult, AmountResult, \
    RateResult
from measr.sysres import TransmittedPacketsResult, ProcessResources, HostResources
from translt.fieldelms import FieldnameProcessor, DictionaryTransition
from translt.fields import CompositeField, IntegerField, FloatField


class ResultCategory(IntEnum):
    quantity = 1
    event    = 2
    time     = 3


class ResultPrefixProcessor(FieldnameProcessor):
    def __init__(self, quantity, event=None, others=None):
        self.quantity = quantity
        self.event    = event  if event  is not None else quantity
        self.others   = others if others is not None else self.event

    def __call__(self, field_name_parts, category):
        field_name_parts.insert( 0, self._prefix( category ) )

        return field_name_parts

    def revoke(self, field_name, category=None):
        return field_name[ len( self._prefix( category ) ): ]

    def _prefix(self, category):
        if category==ResultCategory.quantity:
            return self.quantity
        elif category==ResultCategory.event:
            return self.event
        else:
            return self.others


class ResultCompositeField(CompositeField):
    pass


class StatisticalAccumulatorField(ResultCompositeField):
    def __init__(self, config, typ=StatisticalAccumulator):
        super().__init__( typ, config )

    def init_cnt(self, field=IntegerField):
        return self.init_field( field, 'cnt' ).attribute().category( ResultCategory.event )

    def init_sum(self, field=FloatField):
        return self.init_field( field, 'sum' ).attribute().category( ResultCategory.quantity )

    def init_min(self, field=FloatField):
        return self.init_field( field, 'min' ).attribute().category( ResultCategory.quantity )

    def init_max(self, field=FloatField):
        return self.init_field( field, 'max' ).attribute().category( ResultCategory.quantity )

    def init_mean(self, field=FloatField):
        return self.init_field( field, 'mean' ).attribute().no_input().category( ResultCategory.quantity )

    def init_approx_stddev(self, field=FloatField):
        return self.init_field( field, 'approx_stddev' ).attribute().category( ResultCategory.quantity )


class FrequencyField(CompositeField):
    def __init__(self, config, bin_names, typ=FrequencySink):
        super().__init__( self._do_create_object, config )

        self._type = typ

        for i, bn in enumerate( bin_names ):
            self.start_float( i ).name( bn ).getter( DictionaryTransition ).construct()

    def _do_create_object(self, *args, **kwargs):
        return self._type( self._get_bins( args ), args )

    @abstractmethod
    def _get_bins(self, args):
        ...


class IntervalResultField(ResultCompositeField):
    def __init__(self, config, typ=IntervalResult):
        super().__init__( typ, config )

    def _do_process(self, attr, proc):
        if attr.attr_name=='summary':
            return attr.processed( proc, True )
        else:
            return super()._do_process( attr, proc )

    def init_duration(self, field):
        return self.init_field( field, 'duration' ).attribute().construct().category( ResultCategory.time )

    def init_summary(self, field):
        return self.init_field( field, 'summary' ).attribute().construct()


class DurationResultField(IntervalResultField):
    def __init__(self, config, typ=DurationResult):
        super().__init__( config, typ )

    def init_events_per_sec(self, field=FloatField):
        return self.init_field( field, 'events_per_sec' ).attribute().no_input().category( ResultCategory.event )


class AmountResultField(IntervalResultField):
    def __init__(self, config, typ=AmountResult):
        super().__init__( config, typ )

    def init_events_per_sec(self, field=FloatField):
        return self.init_field( field, 'events_per_sec' ).attribute().no_input().category( ResultCategory.event )

    def init_amount_per_sec(self, field=FloatField):
        return self.init_field( field, 'amount_per_sec' ).attribute().no_input().category( ResultCategory.quantity )


class RateResultField(IntervalResultField):
    def __init__(self, config, typ=RateResult):
        super().__init__( config, typ )


class TransmittedPacketsResultField(CompositeField):
    def __init__(self, config):
        super().__init__( TransmittedPacketsResult, config )

    def init_sent(self, field):
        return self.init_field( field, 'sent' ).attribute().construct()

    def init_recv(self, field):
        return self.init_field( field, 'recv' ).attribute().construct()


class ProcessResourcesField(CompositeField):
    def __init__(self, config):
        super().__init__( ProcessResources, config )

    def init_cpuload(self, field):
        return self.init_field( field, 'cpu_load' ).attribute().construct()

    def start_systemshare(self):
        return self.start_float( 'system_share' ).attribute().construct()

    def init_threadclassrates(self, field):
        return self.init_field( field, 'tcls_rates' ).attribute().construct()

    def start_mem_rss(self):
        return self.start_integer( 'mem_rss' ).attribute().construct()

    def start_mem_vms(self):
        return self.start_integer( 'mem_vms' ).attribute().construct()


class HostResourcesField(CompositeField):
    def __init__(self, config):
        super().__init__( HostResources, config )

    def start_cpuload_mean(self, field):
        return self.start_float( 'cpu_load_mean' ).attribute().construct()

    def init_cpuload(self, field):
        return self.init_field( field, 'cpu_load' ).attribute().construct()

    def init_cpuload_frequencies(self, field):
        return self.init_field( field, 'cpu_load_freq' ).attribute().construct()

    def init_cputimerates(self, field):
        return self.init_field( field, 'cpu_time_rates' ).attribute().construct()

    def init_nicstats(self, field):
        return self.init_field( field, 'nic_stats' ).attribute().construct()
