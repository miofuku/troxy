package reptor.chronos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Asynchronous methods can be invoked by any orphic. Performing the actual work entailed by the invocation
// is postponed. The master of the called orphic is informed that the orphic has undone work.
// Includes @Commutative and in case of methods also @Activating.
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Asynchronous
{

}
