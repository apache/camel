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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The stream resequencer must keep working when its route is stopped and started while a message waits for its timeout,
 * callers waiting for free capacity must be released when the route stops, and a stop must end the delivery thread.
 */
public class StreamResequencerRestartTest extends ContextTestSupport {

    @Test
    public void testTimeoutExpiresAfterRouteRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("msg1");
        template.sendBodyAndHeader("direct:start", "msg1", "seqnum", 1L);
        // the first message waits for the timeout, as there is no earlier message to compare it with
        mock.assertIsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("msg3", "msg4", "msg5");
        // msg2 is missing, so msg3 waits for its timeout, and the route is restarted meanwhile
        template.sendBodyAndHeader("direct:start", "msg3", "seqnum", 3L);
        context.getRouteController().stopRoute("stream");
        context.getRouteController().startRoute("stream");
        template.sendBodyAndHeader("direct:start", "msg4", "seqnum", 4L);
        template.sendBodyAndHeader("direct:start", "msg5", "seqnum", 5L);

        mock.assertIsSatisfied();
    }

    @Test
    public void testCallerWaitingForCapacityIsReleasedOnStop() throws Exception {
        // the waiting caller is inflight, so the graceful stop of the route waits for it until the shutdown timeout
        context.getShutdownStrategy().setTimeout(1);

        // capacity 1: msg2 waits for msg1, and the next caller waits for free capacity
        template.sendBodyAndHeader("direct:capacity", "msg2", "seqnum", 2L);
        ExecutorService executor = context.getExecutorServiceManager().newSingleThreadExecutor(this, "sender");
        try {
            Future<Exchange> sender = executor.submit(() -> template.send("direct:capacity", e -> {
                e.getMessage().setBody("msg3");
                e.getMessage().setHeader("seqnum", 3L);
            }));
            await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("capacity") == 1);

            context.getRouteController().stopRoute("capacity");

            Exchange out = sender.get(10, TimeUnit.SECONDS);
            assertInstanceOf(RejectedExecutionException.class, out.getException());
        } finally {
            context.getExecutorServiceManager().shutdownNow(executor);
        }
    }

    @Test
    public void testDeliveryThreadEndsOnStop() throws Exception {
        // only the delivery thread of the route under test is left running
        context.getRouteController().stopRoute("stream");
        context.getRouteController().stopRoute("capacity");
        await().atMost(10, TimeUnit.SECONDS).until(() -> deliveryThreads() == 1);

        // stopped and started again within the delivery attempt interval
        for (int i = 0; i < 3; i++) {
            context.getRouteController().stopRoute("delivery");
            context.getRouteController().startRoute("delivery");
        }

        // only the delivery thread of the last start is left
        await().atMost(10, TimeUnit.SECONDS).until(() -> deliveryThreads() == 1);
        context.getRouteController().stopRoute("delivery");
        await().atMost(10, TimeUnit.SECONDS).until(() -> deliveryThreads() == 0);
    }

    private long deliveryThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> t.getName().contains("(" + context.getName() + ")"))
                .filter(t -> t.getName().endsWith("Resequencer Delivery"))
                .count();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("stream")
                        .resequence(header("seqnum")).stream().timeout(1000).deliveryAttemptInterval(10)
                        .to("mock:result");

                from("direct:capacity").routeId("capacity")
                        .resequence(header("seqnum")).stream().capacity(1).timeout(60000).deliveryAttemptInterval(10)
                        .to("mock:capacity");

                from("direct:delivery").routeId("delivery")
                        .resequence(header("seqnum")).stream().deliveryAttemptInterval(1000)
                        .to("mock:delivery");
            }
        };
    }
}
