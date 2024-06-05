package reptor.measr;

// -- A meter measures a quantity and yields values, single measured quantity values for it.
// -- (It's not directly a value source because it is not necessarily observable.)
// -- Usually, the last measured value should be directly accessible via the meter itself until
// -- a new measurement is conducted or the meter is somehow reset. It is questionable if the
// -- meter therefore becomes also a measured quantity value, even if the accessors have the
// -- same syntax (for instance, getElapsedTime() could be part of a duration interface as well as
// -- of an interface of a meter that measures durations). The point is that quantity values should
// -- be unmodifiable value types, whereas last value measured by a meter changes (thus the meter
// -- interface would more precisely contain a getElepasedTimeOfLastMeasurement()).
// -- If it stores values, it is also value collector. Maybe we should stipulate this approach by
// -- deriving Meter from ValueCollector, by I'm not sure, if this would bring real advantages for
// -- applications.
public interface Meter
{
}
