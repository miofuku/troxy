package reptor.chronos;

// Synchronous operation : Directly executed to the time of invocation.
// Asynchronous operation: Execution is deferred to another time.
// Object: Synchronous and asynchronous operations on mutable state issued from different contexts.
// ChronosObject: At each point in (chronos) time, such an object is part of exactly one domain.
//                -> Synchronous and asynchronous operations on mutable state issued from a single (but maybe
//                   changing) context.
// Each chronos object could be provided with (current) domain and could check if it is accessed from within
// the same domain.
public interface Orphic
{

}
