package org.apache.camel.spi;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * An internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 * <li>Execute {@link UnitOfWork}</li>
 * <li>Keeping track which route currently is being routed</li>
 * <li>Execute {@link RoutePolicy}</li>
 * <li>Gather JMX performance statics</li>
 * <li>Tracing</li>
 * <li>Debugging</li>
 * <li>Message History</li>
 * <li>Stream Caching</li>
 * <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 * <p/>
 *
 * This is intended for internal use only - do not use this.
 */
public interface InternalProcessor extends Processor {

    @Override
    default void process(Exchange exchange) throws Exception {
        // not in use
    }

    /**
     * Asynchronous API
     */
    boolean process(Exchange exchange, AsyncCallback originalCallback, AsyncProcessor processor, Processor resultProcessor);

    /**
     * Synchronous API
     */
    void process(Exchange exchange, AsyncProcessor processor, Processor resultProcessor);

}
