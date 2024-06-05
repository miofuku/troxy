#!/usr/bin/env python3.4

from abc import ABCMeta, abstractmethod
from collections import namedtuple
from collections.abc import Sequence


class MeasuredValue:
    __slots__ = ()


class ValueSink(metaclass=ABCMeta):
    __slots__ = ()

    @abstractmethod
    def accept(self, value):
        ...


class AccumulatedValueMixin:
    __slots__ = ()

    @property
    def mean(self):
        return self.sum/self.cnt if self.cnt else None

    @classmethod
    def aggregate(cls, summaries):
        aggcnt = 0
        aggsum = 0

        for s in summaries:
            aggcnt += s.cnt
            aggsum += s.sum

        return cls( aggcnt, aggsum )

    def __str__(self):
        mean = '{:.1f}'.format( self.mean ) if self.mean is not None else None
        return '({}*{}={})'.format( self.cnt, mean, self.sum )


class AccumulatedValue(namedtuple( 'AccumulatedValue', 'cnt sum' ), AccumulatedValueMixin):
    __slots__ = ()

    @classmethod
    def accu(cls):
        return ValueAccumulator()

    @classmethod
    def for_accu(cls, accu):
        return cls( accu.cnt, accu.sum )

    def __add__(self, value):
        return __class__( self.cnt+value[ 0 ], self.sum+value[ 1 ] )

    def __sub__(self, value):
        return __class__( self.cnt-value[ 0 ], self.sum-value[ 1 ] )


class ValueAccumulator(AccumulatedValueMixin, ValueSink):
    __slots__ = 'cnt', 'sum'

    def __init__(self, cnt=0, sum_=0):
        self.cnt = cnt
        self.sum = sum_

    def add(self, cnt, sum_):
        self.cnt += cnt
        self.sum += sum_

    def accept(self, value):
        self.cnt += 1
        self.sum += value

    def reset(self):
        self.cnt = self.sum = 0

    def value(self):
        return AccumulatedValue( self.cnt, self.sum )

    @classmethod
    def aggregate(cls, summaries):
        aggcnt = 0
        aggsum = 0

        for s in summaries:
            aggcnt += s.cnt
            aggsum += s.sum

        return cls( aggcnt, aggsum )

    def __repr__(self):
        return '{}@{:x}({}, {})'.format( self.__class__.__name__, id(self), self.cnt, self.sum )


class StatisticalSummaryMixin(AccumulatedValueMixin):
    __slots__ = ()

    def __str__(self):
        mean = '{:.1f}'.format( self.mean ) if self.mean is not None else None
        return '({}*{}={};{}-{};{})'.format(
                self.cnt, mean, self.sum, self.min, self.max, self.approx_stddev )

    @classmethod
    def aggregate(cls, summaries):
        stddevcnt = 0
        stddevsum = 0
        aggcnt    = 0
        aggsum    = 0
        aggmin    = None
        aggmax    = None
        aggstddev = None

        for s in summaries:
            aggcnt += s.cnt
            aggsum += s.sum
            aggmin  = min( aggmin, s.min ) if aggmin is not None else s.min
            aggmax  = max( aggmax, s.max ) if aggmax is not None else s.max

            if s.approx_stddev is not None:
                stddevcnt += 1
                stddevsum += s.approx_stddev

        if stddevcnt:
            aggstddev = stddevsum/stddevcnt

        return cls( aggcnt, aggsum, aggmin, aggmax, aggstddev )


class StatisticalSummary(namedtuple( 'StatisticalSummary', 'cnt sum min max approx_stddev' ), StatisticalSummaryMixin):
    __slots__ = ()

    @classmethod
    def accu(cls):
        return StatisticalAccumulator()

    @classmethod
    def for_accu(cls, accu):
        return cls( accu.cnt, accu.sum, accu.min, accu.max, accu.approx_stddev )


class StatisticalAccumulator(StatisticalSummaryMixin, ValueAccumulator):
    __slots__ = 'min', 'max', 'approx_stddev'

    @classmethod
    def accu(cls):
        return cls

    @classmethod
    def for_accu(cls, accu):
        return cls( accu.cnt, accu.sum, accu.min, accu.max, accu.approx_stddev )

    def __init__(self, cnt=0, sum_=0, min_=None, max_=None, approx_stddev=None):
        super().__init__( cnt, sum_ )

        self.min = min_
        self.max = max_
        self.approx_stddev = approx_stddev

    def accept(self, value):
        super().accept( value )

        if self.cnt==1:
            self.min = self.max = value
        elif value<self.min:
            self.min = value
        elif value>self.max:
            self.max = value

    def reset(self):
        super().reset()

        self.min = self.max = self.approx_stddev = None

    def summary(self):
        return StatisticalSummary( self.cnt, self.sum, self.min, self.max, self.approx_stddev )

    def __repr__(self):
        return '{}@{:x}({}, {}, {}, {}, {})'.format( self.__class__.__name__, id(self),
                self.cnt, self.sum, self.min, self.max, self.approx_stddev )


class FrequencySink(Sequence, ValueSink):
    def __init__(self, bins, init=None):
        assert init is None or len( init )==len( bins ) + 1
        self.bins   = bins
        self.counts = list( init ) if init else [ 0 ] * (len( bins ) + 1)

    @classmethod
    def aggregate(cls, freqs):
        bins   = freqs[ 0 ].bins
        counts = list( freqs[ 0 ].counts )

        for r in freqs[ 1: ]:
            assert r.bins==bins
            for i in range( len( counts ) ):
                counts[ i ] += r.counts[ i ]

        return cls( bins, counts )

    def __len__(self):
        return len( self.counts )

    def __getitem__(self, index):
        return self.counts[ index ]

    def accept(self, value):
        for i, t in enumerate( self.bins ):
            if value<=t:
                self.counts[ i ] += 1
                break
        else:
            self.counts[ -1 ] += 1

    def reset(self):
        self.counts = [ 0 ] * (len( self.bins ) + 1)


class IntervalResult(namedtuple( 'IntervalResult', 'duration summary' )):
    __slots__ = ()

    @classmethod
    def for_value(cls, duration, cnt, sum_):
        return cls( duration, AccumulatedValue( cnt, sum_ ) )

    @classmethod
    def aggregate(cls, results):
        if len( results )==1:
            return results[ 0 ]

        maxdur = max( [ r.duration for r in results ] )
        aggval = results[ 0 ].summary.accu().aggregate( [ r.summary for r in results if r.duration ] )

        aggval.cnt = aggval.sum = 0.0
        for r in results:
            if r.duration:
                f = maxdur / r.duration
                aggval.cnt += r.summary.cnt*f
                aggval.sum += r.summary.sum*f

        aggval.cnt = round( aggval.cnt )

        return cls( maxdur, results[ 0 ].summary.for_accu( aggval ) )

    @property
    def _dur_in_sec(self):
        return self.duration/1000000000

    @property
    def _events_per_sec(self):
        return self.summary.cnt/self._dur_in_sec if self.duration else None

    @property
    def _amount_per_sec(self):
        return self.summary.sum/self._dur_in_sec if self.duration else None

    @property
    def mean(self):
        return self.summary.mean

    @property
    def min(self):
        return self.summary.min

    @property
    def max(self):
        return self.summary.max

    @property
    def approx_stddev(self):
        return self.summary.approx_stddev


class DurationResult(IntervalResult):
    __slots__ = ()

    @property
    def events_per_sec(self):
        return self._events_per_sec


class AmountResult(IntervalResult):
    __slots__ = ()

    @property
    def events_per_sec(self):
        return self._events_per_sec

    @property
    def amount_per_sec(self):
        return self._amount_per_sec


class RateResult(IntervalResult):
    __slots__ = ()
