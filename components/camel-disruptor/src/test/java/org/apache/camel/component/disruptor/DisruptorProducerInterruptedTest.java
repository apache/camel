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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A disruptor producer that is interrupted while it waits for the reply must fail the exchange, and not return the
 * request as the reply.
 */
class DisruptorProducerInterruptedTest extends CamelTestSupport {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch releaseConsumer = new CountDownLatch(1);
    private final CountDownLatch consumerDone = new CountDownLatch(1);

    @AfterEach
    void releaseAndShutdown() {
        releaseConsumer.countDown();
        executor.shutdownNow();
    }

    @Test
    void testInterruptedWhileWaitingForReply() throws Exception {
        AtomicReference<Thread> sender = new AtomicReference<>();
        Future<Exchange> future = executor.submit(() -> {
            sender.set(Thread.currentThread());
            Exchange exchange = context.getEndpoint("disruptor:slow").createExchange(ExchangePattern.InOut);
            exchange.getMessage().setBody("request");
            return template.send("disruptor:slow?timeout=0", exchange);
        });
        // wait until the sender waits for the reply in the disruptor producer
        await().atMost(10, TimeUnit.SECONDS).until(() -> isWaitingInDisruptorProducer(sender.get()));
        sender.get().interrupt();
        Exchange out = future.get(10, TimeUnit.SECONDS);

        // the request must not be returned as the reply
        assertInstanceOf(InterruptedException.class, out.getException());

        // and the reply from the consumer is ignored when it completes later
        releaseConsumer.countDown();
        assertTrue(consumerDone.await(10, TimeUnit.SECONDS));
        assertInstanceOf(InterruptedException.class, out.getException());
        assertEquals("request", out.getMessage().getBody());
    }

    private static boolean isWaitingInDisruptorProducer(Thread thread) {
        if (thread == null || thread.getState() != Thread.State.WAITING) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (DisruptorProducer.class.getName().equals(element.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:slow").routeId("slow")
                        .process(e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onDone(Exchange exchange) {
                                consumerDone.countDown();
                            }
                        }))
                        .process(e -> releaseConsumer.await(20, TimeUnit.SECONDS))
                        .setBody(constant("reply"));
            }
        };
    }
}
