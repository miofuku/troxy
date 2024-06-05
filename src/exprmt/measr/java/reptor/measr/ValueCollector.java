package reptor.measr;

// -- A collector collects measured quantity values (produced by itself if it is a meter or obtained
// -- somehow other) and stores them in an aggregated form or as a collection of single values.
public interface ValueCollector
{
    void reset();
}
