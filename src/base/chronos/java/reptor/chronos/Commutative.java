package reptor.chronos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


// Don't know, if that term is correct. Would have to read
// "The Scalable Commutativity Rule: Designing Scalable Software for Multicore Processors"; and even then...
// The operations of commutative objects are not necessarily side-effect-free. Their invocation is allowed
// to change internal state. However, concurrent invocations of these operations do not interfere in the sense
// that all possible execution orders lead to equivalent (not necessarily the same) results.
// Note, that does not mean that commutative objects must be thread-safe.
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Commutative
{

}
