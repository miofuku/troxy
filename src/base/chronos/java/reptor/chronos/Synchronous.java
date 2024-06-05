package reptor.chronos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Synchronous methods must be only invoked within the orphic hierarchy, that is,
// by masters or master masters. The work is carried out immediately.
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Synchronous
{

}
