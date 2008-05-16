package org.apache.camel.component.atom;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * Base class for consuming Atom feeds.
 */
public abstract class AtomConsumerSupport extends ScheduledPollConsumer<Exchange> {
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;
    protected final AtomEndpoint endpoint;

    public AtomConsumerSupport(AtomEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

}
