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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With asyncDelayedRedelivery the redelivery waits on a scheduled task instead of a sleeping thread. Stopping the route
 * must then reject the pending redelivery (allowRedeliveryWhileStopping=false), the same as for a synchronous
 * redelivery, and not wait for the redelivery delay and then redeliver.
 */
public class NotAllowRedeliveryWhileStoppingAsyncDelayedTest extends ContextTestSupport {

    private final CountDownLatch redeliveryScheduled = new CountDownLatch(1);
    private final AtomicInteger redeliveries = new AtomicInteger();
    private final ScheduledThreadPoolExecutor redeliveryPool = new ScheduledThreadPoolExecutor(1) {
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            ScheduledFuture<?> answer = super.schedule(command, delay, unit);
            redeliveryScheduled.countDown();
            return answer;
        }
    };

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("redeliveryPool", redeliveryPool);
        return registry;
    }

    @AfterEach
    public void shutdownPool() {
        redeliveryPool.shutdownNow();
    }

    @Test
    public void testStopRouteRejectsScheduledRedelivery() throws Exception {
        testStopRejectsScheduledRedelivery(() -> context.getRouteController().stopRoute("foo"));
    }

    @Test
    public void testStopCamelContextRejectsScheduledRedelivery() throws Exception {
        testStopRejectsScheduledRedelivery(() -> context.stop());
    }

    private void testStopRejectsScheduledRedelivery(StopAction stop) throws Exception {
        // a graceful stop that waits for the redelivery delay runs into this timeout
        context.getShutdownStrategy().setTimeout(5);

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(1);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        foo.assertIsSatisfied();
        // the first attempt failed and the redelivery is scheduled (in 60 seconds, the default maximum redelivery delay)
        assertTrue(redeliveryScheduled.await(20, TimeUnit.SECONDS));

        stop.run();

        // the pending redelivery is not allowed while stopping, so the message goes to the dead letter channel
        dead.assertIsSatisfied();
        Exchange exchange = dead.getReceivedExchanges().get(0);
        Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertNotNull(cause);
        assertInstanceOf(RejectedExecutionException.class, cause);
        assertEquals("Redelivery not allowed while stopping", cause.getMessage());
        // and is not redelivered
        assertEquals(0, redeliveries.get());
        foo.assertIsSatisfied();
        assertEquals(0, context.getInflightRepository().size());
    }

    @FunctionalInterface
    private interface StopAction {
        void run() throws Exception;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(5).redeliveryDelay(TimeUnit.HOURS.toMillis(1))
                        .asyncDelayedRedelivery().executorServiceRef("redeliveryPool")
                        .allowRedeliveryWhileStopping(false)
                        .onRedelivery(e -> redeliveries.incrementAndGet()));

                from("seda:start").routeId("foo").to("mock:foo").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
