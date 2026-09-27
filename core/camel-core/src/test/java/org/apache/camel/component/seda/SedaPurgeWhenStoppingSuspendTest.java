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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suspending a route must not discard the pending messages of a seda consumer with purgeWhenStopping=true, the option
 * only applies when stopping.
 */
public class SedaPurgeWhenStoppingSuspendTest extends ContextTestSupport {

    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);
    private final CountDownLatch pendingChecked = new CountDownLatch(1);
    private final AtomicBoolean suspending = new AtomicBoolean();

    private final LinkedBlockingQueue<Exchange> queue = new LinkedBlockingQueue<>() {
        @Override
        public int size() {
            // the shutdown strategy asks the seda consumer for its pending exchanges (the queue size)
            if (suspending.get()) {
                pendingChecked.countDown();
            }
            return super.size();
        }
    };

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("myQueue", queue);
        return registry;
    }

    @Test
    public void testSuspendDoesNotPurge() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A", "B", "C", "D", "E");

        for (String body : new String[] { "A", "B", "C", "D", "E" }) {
            template.sendBody("seda:foo", body);
        }
        // A is being processed and B..E are pending on the queue
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            suspending.set(true);
            Future<?> suspend = executor.submit(() -> {
                context.getRouteController().suspendRoute("myRoute");
                return null;
            });
            // let A complete when the shutdown strategy is waiting for the pending exchanges
            assertTrue(pendingChecked.await(10, TimeUnit.SECONDS));
            releaseFirst.countDown();

            suspend.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // the pending messages were not discarded
        mock.assertIsSatisfied();
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("myRoute"));

        // and the route works after resume
        mock.reset();
        mock.expectedBodiesReceived("F");
        context.getRouteController().resumeRoute("myRoute");
        template.sendBody("seda:foo", "F");
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo?queue=#myQueue&purgeWhenStopping=true").routeId("myRoute")
                        .process(exchange -> {
                            if ("A".equals(exchange.getMessage().getBody(String.class))) {
                                firstStarted.countDown();
                                releaseFirst.await(10, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
