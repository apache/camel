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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch resequencer must deliver one exchange at a time, in order, when its route is stopped and started while the
 * sender thread of the previous start is still delivering an exchange, and that sender must end.
 */
public class BatchResequencerRestartTest extends ContextTestSupport {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch firstInside = new CountDownLatch(1);
    private final CountDownLatch otherStarted = new CountDownLatch(1);
    private volatile boolean restoreInterrupt = true;

    @Test
    public void testRestartWhileDeliveringKeepsOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1", "2", "3", "4");

        restartWhileDeliveringFirst();

        mock.assertIsSatisfied();
        assertEquals(List.of("start-1", "end-1", "start-2", "end-2", "start-3", "end-3", "start-4", "end-4"), events);
    }

    @Test
    public void testRestartTwiceWhileDeliveringKeepsOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1", "2", "3", "4");

        restartWhileDeliveringFirst(2);

        mock.assertIsSatisfied();
        assertEquals(List.of("start-1", "end-1", "start-2", "end-2", "start-3", "end-3", "start-4", "end-4"), events);
        await().atMost(10, TimeUnit.SECONDS).until(() -> senderThreads() == 1);
    }

    @Test
    public void testSenderThreadEndsWhenRestartedWhileDelivering() throws Exception {
        // the processing of the first exchange does not keep the interrupt status of the thread, as some libraries do
        restoreInterrupt = false;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("1", "2", "3", "4");

        restartWhileDeliveringFirst();

        mock.assertIsSatisfied();
        // only the sender of the last start is left
        await().atMost(10, TimeUnit.SECONDS).until(() -> senderThreads() == 1);
    }

    private void restartWhileDeliveringFirst() throws Exception {
        restartWhileDeliveringFirst(1);
    }

    private void restartWhileDeliveringFirst(int restarts) throws Exception {
        // a batch of 2: the sender delivers 1, and 2 waits in the batch
        template.sendBodyAndHeader("direct:start", "1", "seq", 1);
        template.sendBodyAndHeader("direct:start", "2", "seq", 2);
        assertTrue(firstInside.await(10, TimeUnit.SECONDS));

        // the route is stopped and started while 1 is being delivered (the stop does not wait for it)
        for (int i = 0; i < restarts; i++) {
            context.getRouteController().stopRoute("batch");
            context.getRouteController().startRoute("batch");
        }

        template.sendBodyAndHeader("direct:start", "3", "seq", 3);
        template.sendBodyAndHeader("direct:start", "4", "seq", 4);
    }

    private void deliver(Exchange exchange) {
        String body = exchange.getMessage().getBody(String.class);
        events.add("start-" + body);
        if ("1".equals(body)) {
            firstInside.countDown();
            // the window in which no other exchange may start while 1 is processed
            boolean interrupted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (otherStarted.getCount() > 0 && System.nanoTime() < deadline) {
                try {
                    otherStarted.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted && restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        } else {
            otherStarted.countDown();
        }
        events.add("end-" + body);
    }

    private long senderThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> t.getName().contains("(" + context.getName() + ")"))
                .filter(t -> t.getName().endsWith("Batch Sender"))
                .count();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("batch")
                        .resequence(header("seq")).batch().size(2).timeout(60000)
                        .process(BatchResequencerRestartTest.this::deliver)
                        .to("mock:result");
            }
        };
    }
}
