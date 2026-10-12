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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.PooledExchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.PooledObjectFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that gives back an exchange it never routed (a batch cut down to maxMessagesPerPoll, a consumer that is
 * stopping, a failure while the message is prepared) must put it into the pool once, so that the next two exchanges are
 * two different objects.
 */
class PooledExchangeFactoryReleaseNotRoutedTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.getExchangeFactory().setStatisticsEnabled(true);
        return camelContext;
    }

    @Test
    void autoReleaseExchangeThatWasNotRoutedIsPooledOnce() {
        Consumer consumer = context.getRoute("foo").getConsumer();

        Exchange exchange = consumer.createExchange(true);
        exchange.getMessage().setBody("not routed");
        consumer.releaseExchange(exchange, true);

        assertPooledOnce(consumer, exchange);
    }

    @Test
    void autoReleaseExchangeThatWasNotRoutedIsPooledOnceWhenReleasedAsNotAutoRelease() {
        Consumer consumer = context.getRoute("foo").getConsumer();

        // several consumers create their exchanges with auto release and give back the ones they do not route with false
        Exchange exchange = consumer.createExchange(true);
        exchange.getMessage().setBody("not routed");
        consumer.releaseExchange(exchange, false);

        assertPooledOnce(consumer, exchange);
    }

    /**
     * Releasing an exchange a second time is still a bug of the consumer. It does no harm here only because nobody took
     * the exchange from the pool in between; otherwise the second release would reset the exchange of another message.
     */
    @Test
    void autoReleaseExchangeThatWasRoutedIsPooledOnceWhenReleasedAgain() throws Exception {
        Consumer consumer = context.getRoute("foo").getConsumer();

        Exchange exchange = consumer.createExchange(true);
        exchange.getMessage().setBody("routed");
        // the unit of work of the route is done here and gives the exchange back to the pool
        consumer.getProcessor().process(exchange);
        assertEquals(1, statistics().getReleasedCounter());

        consumer.releaseExchange(exchange, true);

        assertPooledOnce(consumer, exchange);
    }

    @Test
    void exchangeWithoutAutoReleaseIsPooledOnce() {
        Consumer consumer = context.getRoute("foo").getConsumer();

        Exchange exchange = consumer.createExchange(false);
        exchange.getMessage().setBody("not routed");
        consumer.releaseExchange(exchange, false);

        assertPooledOnce(consumer, exchange);
    }

    @Test
    void releaseReturnsFalseWhenThePoolIsFullAndTheNotRoutedExchangeIsDiscarded() {
        PooledExchangeFactory factory = newFactoryWithCapacityOne();

        Exchange first = factory.create(true);
        Exchange second = factory.create(true);

        assertTrue(factory.release(first), "the pool has room for the first exchange");
        assertFalse(factory.release(second), "the pool is full, so the second exchange is discarded");

        assertEquals(1, factory.getSize(), "exchanges in the pool");
        assertEquals(1, factory.getStatistics().getReleasedCounter());
        assertEquals(1, factory.getStatistics().getDiscardedCounter());

        // the discarded exchange was reset
        assertEquals(0, second.getClock().getCreated());
        assertSame(first, factory.create(true));
        assertNotSame(first, factory.create(true));
    }

    @Test
    void releaseReturnsTrueWhenTheExchangeWasAlreadyGivenBackByItsUnitOfWork() {
        PooledExchangeFactory factory = newFactoryWithCapacityOne();

        Exchange routed = factory.create(true);
        // what the unit of work does when the route is done
        ((PooledExchange) routed).done();
        assertEquals(1, factory.getSize(), "exchanges in the pool");

        assertTrue(factory.release(routed), "nothing to do: the exchange is in the pool already");

        assertEquals(1, factory.getSize(), "exchanges in the pool");
        assertEquals(1, factory.getStatistics().getReleasedCounter());
        assertEquals(0, factory.getStatistics().getDiscardedCounter());
    }

    private PooledExchangeFactory newFactoryWithCapacityOne() {
        PooledExchangeFactory factory = new PooledExchangeFactory();
        factory.setCamelContext(context);
        factory.setCapacity(1);
        factory.setStatisticsEnabled(true);
        // build creates the pool; the factory is not started, as it has no consumer to be registered with
        factory.build();
        return factory;
    }

    private void assertPooledOnce(Consumer consumer, Exchange released) {
        // the exchange was reset
        assertNull(released.getMessage().getBody());

        assertEquals(1, manager().getPooledCounter(), "exchanges in the pool");
        assertEquals(1, statistics().getCreatedCounter());
        assertEquals(1, statistics().getReleasedCounter());
        assertEquals(0, statistics().getDiscardedCounter());

        Exchange first = consumer.createExchange(true);
        Exchange second = consumer.createExchange(true);

        assertSame(released, first, "the pooled exchange is used again");
        assertNotSame(first, second, "two messages must not share one exchange");
        assertEquals(0, manager().getPooledCounter(), "exchanges in the pool");
        assertEquals(2, statistics().getCreatedCounter());
        assertEquals(1, statistics().getAcquiredCounter());
    }

    private ExchangeFactoryManager manager() {
        return context.getCamelContextExtension().getExchangeFactoryManager();
    }

    private PooledObjectFactory.Statistics statistics() {
        return manager().getStatistics();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo").to("mock:result");
            }
        };
    }
}
