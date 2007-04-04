package org.apache.camel.component.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to mark a method to be invoked when an entity bean has been succesfully processed
 * by a Camel consumer, so that it can be updated in some way to remove it from the query set.
 * <p/>
 * For example a method may be marked to set an active flag to false or to update some status value to the next step in a workflow
 *
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Consumed {
}
