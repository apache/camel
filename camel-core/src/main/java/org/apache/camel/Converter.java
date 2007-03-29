package org.apache.camel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * An annotation used to mark classes and methods to indicate code capable of converting from a type to another type
 * which are then auto-discovered using the <a href="http://activemq.apache.org/camel/type-converter.html">Type Conversion Support</a>
 *
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Converter {
}
