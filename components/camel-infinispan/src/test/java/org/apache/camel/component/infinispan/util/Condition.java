package org.apache.camel.component.infinispan.util;

/**
 * @author Martin Gencur
 */
public interface Condition {
    boolean isSatisfied() throws Exception;
}
