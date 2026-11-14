/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.sanitizer;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * With pooled exchanges the sanitizer stays off: a pooled exchange keeps its unit of work for its next message (it is
 * reset, not replaced), and the consumer adds the commit of that message to it before the exchange is given it again,
 * so rule R2, which follows the commits by unit of work, would check only the first message of each pooled exchange and
 * could match the records of a message with the next one. The processor exchange factory pools the copies of the EIPs,
 * so a pooled one alone keeps it off too.
 */
@CamelSanitizer
class SanitizerPooledExchangesTest extends ContextTestSupport {

    private final AtomicInteger runs = new AtomicInteger();
    private final Synchronization shared = new SynchronizationAdapter() {
        @Override
        public void onDone(Exchange exchange) {
            runs.incrementAndGet();
        }
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        // as exchangeFactory=pooled in camel-main
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        context.addEndpoint("pooled:x", new PooledEndpoint("pooled:x", context));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("pooled:x").routeId("pooled").to("mock:pooled");
            }
        };
    }

    @Test
    void theSanitizerStaysOffWithPooledExchanges() throws Exception {
        getMockEndpoint("mock:pooled").expectedMessageCount(3);
        PooledConsumer consumer = pooledConsumer();

        Exchange first = consumer.receive("a", shared);
        Exchange second = consumer.receive("b", shared);
        consumer.receive("c", shared);

        assertMockEndpointsSatisfied();
        // the same pooled exchange, and so the same unit of work, for each message
        assertSame(first, second);
        assertEquals(3, runs.get());
        assertNull(Sanitizer.get(context), "the sanitizer must stay off with pooled exchanges");
    }

    @Test
    void theSanitizerStaysOffWithAPooledProcessorExchangeFactoryAlone() throws Exception {
        // built on the thread of the test class, so it gets the sanitizer unless it stays off
        CamelContext other = new DefaultCamelContext();
        other.getCamelContextExtension().setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        try {
            other.start();
            assertFalse(other.getCamelContextExtension().getExchangeFactory().isPooled());
            assertNull(Sanitizer.get(other), "the sanitizer must stay off with pooled copies");
        } finally {
            other.stop();
        }
    }

    @Test
    void theSanitizerIsOnWithoutPooledExchanges() throws Exception {
        // control: the same context without pooled exchanges gets the sanitizer
        CamelContext other = new DefaultCamelContext();
        try {
            other.start();
            assertNotNull(Sanitizer.get(other));
        } finally {
            other.stop();
        }
    }

    private PooledConsumer pooledConsumer() {
        return context.getEndpoint("pooled:x", PooledEndpoint.class).consumer;
    }

    static final class PooledEndpoint extends DefaultEndpoint {

        volatile PooledConsumer consumer;

        PooledEndpoint(String uri, CamelContext camelContext) {
            super(uri, null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("consumer only");
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            consumer = new PooledConsumer(this, processor);
            return consumer;
        }
    }

    /**
     * A consumer that takes its exchanges from the exchange factory, adds its commit before routing each one, and
     * releases the exchange afterwards (back to the pool).
     */
    static final class PooledConsumer extends DefaultConsumer {

        PooledConsumer(PooledEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        Exchange receive(String body, Synchronization commit) throws Exception {
            Exchange exchange = createExchange(false);
            try {
                exchange.getMessage().setBody(body);
                exchange.getExchangeExtension().addOnCompletion(commit);
                getProcessor().process(exchange);
            } finally {
                releaseExchange(exchange, false);
            }
            return exchange;
        }
    }
}
