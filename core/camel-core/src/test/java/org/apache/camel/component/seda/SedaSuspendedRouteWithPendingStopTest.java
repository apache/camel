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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping a suspended seda route must not wait for the messages sent to it while it was suspended, as a suspended
 * consumer does not consume them.
 */
class SedaSuspendedRouteWithPendingStopTest extends ContextTestSupport {

    private final CountDownLatch processing = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void testStopSuspendedRouteWithPendingMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("X");

        // keep the consumer thread busy with X while the consumer is suspended (as a route policy such as
        // ThrottlingInflightRoutePolicy does), so it does not poll the queue while A, B and C are sent
        template.sendBody("seda:start", "X");
        assertTrue(processing.await(10, TimeUnit.SECONDS), "X should be processed");
        SedaEndpoint seda = (SedaEndpoint) context.getRoute("foo").getEndpoint();
        ((SedaConsumer) context.getRoute("foo").getConsumer()).suspend();

        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");
        template.sendBody("seda:start", "C");
        release.countDown();
        mock.assertIsSatisfied();

        // abort the stop if the graceful shutdown times out
        boolean stopped = context.getRouteController().stopRoute("foo", 10, TimeUnit.SECONDS, true);
        assertTrue(stopped, "Route should be stopped without waiting for the shutdown timeout");
        assertFalse(context.getShutdownStrategy().isTimeoutOccurred());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        // the suspended consumer did not process the messages, they are kept on the queue
        assertEquals(3, seda.getQueue().size());

        // and they are processed when the route is started again
        mock.reset();
        mock.expectedBodiesReceived("A", "B", "C");
        context.getRouteController().startRoute("foo");
        mock.assertIsSatisfied();
    }

    @Test
    void testStopContextWithSuspendedRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // keep a reference to the queue, as the endpoint releases it when it is shut down
        BlockingQueue<Exchange> queue = ((SedaEndpoint) context.getRoute("foo").getEndpoint()).getQueue();
        context.getRouteController().suspendRoute("foo");

        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");

        context.getShutdownStrategy().setTimeout(10);
        context.stop();
        assertFalse(context.getShutdownStrategy().isTimeoutOccurred(), "Graceful shutdown should not time out");
        // nothing was lost: a message is either still on the queue (purgeWhenStopping is false), or it was processed
        // (a poll that was already in progress when the route was suspended may still take the first one)
        assertEquals(2, queue.size() + mock.getReceivedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start?pollTimeout=100").routeId("foo")
                        .process(e -> {
                            if ("X".equals(e.getMessage().getBody(String.class))) {
                                processing.countDown();
                                release.await(10, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
