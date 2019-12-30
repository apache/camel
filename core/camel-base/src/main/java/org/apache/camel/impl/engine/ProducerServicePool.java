package org.apache.camel.impl.engine;

import java.util.function.Function;

import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.util.function.ThrowingFunction;

/**
 * {@link org.apache.camel.Producer} pool.
 */
public class ProducerServicePool extends ServicePool<AsyncProducer> {

    public ProducerServicePool(ThrowingFunction<Endpoint, AsyncProducer, Exception> creator, Function<AsyncProducer, Endpoint> getEndpoint, int capacity) {
        super(creator, getEndpoint, capacity);
    }
}
