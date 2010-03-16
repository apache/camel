package org.apache.camel;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/**
 * Represent the kinds of options for rejection handlers for thread pools.
 * <p/>
 * These options are used for fine grained thread pool settings, where you
 * want to control which handler to use when a thread pool cannot execute
 * a new task.
 * <p/>
 * Camel will by default use <tt>CallerRuns</tt>.
 *
 * @version $Revision$
 */
@XmlType
@XmlEnum(String.class)
public enum ThreadPoolRejectedPolicy {

    Abort, CallerRuns, DiscardOldest, Discard;

    public RejectedExecutionHandler asRejectedExecutionHandler() {
        if (this == Abort) {
            return new ThreadPoolExecutor.AbortPolicy();
        } else if (this == CallerRuns) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        } else if (this == DiscardOldest) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        } else if (this == Discard) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        throw new IllegalArgumentException("Unknown ThreadPoolRejectedPolicy: " + this);
    }

}
