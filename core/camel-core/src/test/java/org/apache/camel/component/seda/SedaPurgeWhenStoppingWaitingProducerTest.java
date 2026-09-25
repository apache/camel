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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exchanges discarded by purging the seda queue must be completed, so a producer waiting for their reply is released.
 */
public class SedaPurgeWhenStoppingWaitingProducerTest extends ContextTestSupport {

    private final CountDownLatch busyStarted = new CountDownLatch(1);
    private final CountDownLatch releaseBusy = new CountDownLatch(1);

    @Test
    public void testWaitingProducerReleasedWhenStopping() throws Exception {
        template.sendBody("seda:svc", "busy");
        assertTrue(busyStarted.await(10, TimeUnit.SECONDS));

        // request/reply without timeout, queued behind the busy message
        Exchange request = context.getEndpoint("seda:svc").createExchange(ExchangePattern.InOut);
        request.getMessage().setBody("request");
        Future<Exchange> reply = template.asyncSend("seda:svc?timeout=0", request);
        SedaEndpoint endpoint = context.getEndpoint("seda:svc", SedaEndpoint.class);
        await().atMost(10, TimeUnit.SECONDS).until(() -> endpoint.getQueue().size() == 1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> stop = executor.submit(() -> {
                context.getRouteController().stopRoute("svc");
                return null;
            });

            // the request is discarded by the purge, and the waiting producer is released
            Exchange out = reply.get(10, TimeUnit.SECONDS);
            assertInstanceOf(RejectedExecutionException.class, out.getException());

            releaseBusy.countDown();
            stop.get(20, TimeUnit.SECONDS);
        } finally {
            releaseBusy.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testPurgeQueueCompletesDiscardedExchanges() throws Exception {
        template.sendBody("seda:svc", "busy");
        assertTrue(busyStarted.await(10, TimeUnit.SECONDS));

        CountDownLatch failed = new CountDownLatch(1);
        Exchange inOnly = context.getEndpoint("seda:svc").createExchange(ExchangePattern.InOnly);
        inOnly.getMessage().setBody("inOnly");
        inOnly.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onFailure(Exchange exchange) {
                failed.countDown();
            }
        });
        template.send("seda:svc", inOnly);

        Exchange request = context.getEndpoint("seda:svc").createExchange(ExchangePattern.InOut);
        request.getMessage().setBody("request");
        Future<Exchange> reply = template.asyncSend("seda:svc?timeout=0", request);

        SedaEndpoint endpoint = context.getEndpoint("seda:svc", SedaEndpoint.class);
        await().atMost(10, TimeUnit.SECONDS).until(() -> endpoint.getQueue().size() == 2);

        try {
            // as done from JMX
            endpoint.purgeQueue();

            assertEquals(0, endpoint.getQueue().size());
            assertTrue(failed.await(10, TimeUnit.SECONDS), "on completion of the discarded InOnly exchange should be done");
            Exchange out = reply.get(10, TimeUnit.SECONDS);
            assertInstanceOf(RejectedExecutionException.class, out.getException());
        } finally {
            releaseBusy.countDown();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:svc?purgeWhenStopping=true").routeId("svc")
                        .process(exchange -> {
                            if ("busy".equals(exchange.getMessage().getBody(String.class))) {
                                busyStarted.countDown();
                                releaseBusy.await(20, TimeUnit.SECONDS);
                            }
                        });
            }
        };
    }
}
