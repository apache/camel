package org.apache.camel.impl.engine;

import java.util.function.Function;

import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.util.function.ThrowingFunction;

/**
 * {@link PollingConsumer} pool.
 */
public class PollingConsumerServicePool extends ServicePool<PollingConsumer> {

    public PollingConsumerServicePool(ThrowingFunction<Endpoint, PollingConsumer, Exception> creator, Function<PollingConsumer, Endpoint> getEndpoint, int capacity) {
        super(creator, getEndpoint, capacity);
    }
}
