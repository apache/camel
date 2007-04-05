package org.apache.camel.converter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Used to indicate that the actual type of a parameter on a converter method must have the given annotation class
 * to be applicable. e.g. this annotation could be used on a JAXB converter which only applies to objects with a
 * JAXB annotation on them
 *
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface HasAnnotation {

    Class value();
}
