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

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A receive() without a timeout on a seda endpoint must return once the polling consumer is stopped, and must keep
 * waiting on a polling consumer that was never started.
 */
class SedaPollingConsumerStopTest extends ContextTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Thread receiver;

    @AfterEach
    void stopExecutor() {
        // a receive that still waits (the bug) must not keep the test JVM busy
        executor.shutdownNow();
    }

    @Test
    void testConsumerTemplateReceiveReturnsWhenCamelContextStops() throws Exception {
        Future<Exchange> received = receiveInThread(() -> consumer.receive("seda:idle"));

        context.stop();

        assertNull(received.get(10, TimeUnit.SECONDS));
    }

    @Test
    void testConsumerTemplateReceiveReturnsWhenTemplateStops() throws Exception {
        Future<Exchange> received = receiveInThread(() -> consumer.receive("seda:idle"));

        consumer.stop();

        assertNull(received.get(10, TimeUnit.SECONDS));
    }

    @Test
    void testPollEnrichWithoutTimeoutReturnsWhenRouteStops() throws Exception {
        Future<Exchange> sent = receiveInThread(() -> template.send("direct:start", e -> e.getMessage().setBody("x")));

        // the graceful stop waits for the exchange in pollEnrich, the forced stop then stops the polling consumer
        context.getRouteController().stopRoute("poll", 1, TimeUnit.SECONDS);

        // and the waiting receive() returns, so the exchange completes
        sent.get(10, TimeUnit.SECONDS);
    }

    @Test
    void testReceiveOnPollingConsumerNotStartedKeepsWaiting() throws Exception {
        PollingConsumer pollingConsumer = context.getEndpoint("seda:idle").createPollingConsumer();

        Future<Exchange> received = receiveInThread(pollingConsumer::receive);
        // longer than the interval in which receive checks whether it was stopped
        await().during(1500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(() -> !received.isDone());

        template.sendBody("seda:idle", "hello");

        assertEquals("hello", received.get(10, TimeUnit.SECONDS).getMessage().getBody());
    }

    private Future<Exchange> receiveInThread(Callable<Exchange> receive) {
        CompletableFuture<Exchange> answer = new CompletableFuture<>();
        executor.execute(() -> {
            receiver = Thread.currentThread();
            try {
                answer.complete(receive.call());
            } catch (Exception e) {
                answer.completeExceptionally(e);
            }
        });
        // wait until the thread waits for a message in the seda polling consumer
        await().atMost(10, TimeUnit.SECONDS).until(() -> receiver != null
                && Arrays.stream(receiver.getStackTrace())
                        .anyMatch(f -> f.getClassName().equals(SedaPollingConsumer.class.getName())
                                && f.getMethodName().equals("receive")));
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("poll")
                        .pollEnrich("seda:idle")
                        .to("mock:result");
            }
        };
    }
}
