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
package org.apache.camel.component.vertx.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code synchronous} option on the vertx-websocket consumer: with {@code synchronous=true} the worker thread runs
 * the route until it ends, holding the thread even when an asynchronous step is encountered. This bounds the messages
 * in flight by the size of the worker pool.
 */
class VertxWebsocketSynchronousTest {

    private static final int WORKERS = 2;
    private static final int EXTRA = 1; // one extra message beyond the worker count

    private Vertx vertx;
    private final List<AsyncCallback> waiting = new ArrayList<>();
    private final Object waitingLock = new Object();
    private CountDownLatch arrivedLatch;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(WORKERS));
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    @Test
    void synchronousOptionBoundsMessagesInFlightByWorkerPool() throws Exception {
        // With synchronous=true: each worker thread holds until the route ends,
        // so at most WORKERS messages can be in flight at once
        arrivedLatch = new CountDownLatch(WORKERS);
        try (CamelContext context = new DefaultCamelContext()) {
            context.getShutdownStrategy().setTimeout(5);
            context.getRegistry().bind("vertx", vertx);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("vertx-websocket:localhost:0/test?synchronous=true")
                            .process(new AsyncProcessorSupport() {
                                @Override
                                public boolean process(Exchange exchange, AsyncCallback callback) {
                                    synchronized (waitingLock) {
                                        waiting.add(callback);
                                        arrivedLatch.countDown();
                                    }
                                    return false;
                                }
                            });
                }
            });
            context.start();

            VertxWebsocketComponent component = context.getComponent("vertx-websocket", VertxWebsocketComponent.class);
            int serverPort = component.getVertxHostRegistry().values().iterator().next().getPort();

            ExecutorService senders = Executors.newFixedThreadPool(WORKERS + EXTRA);
            ProducerTemplate template = context.createProducerTemplate();
            try {
                List<CompletableFuture<Void>> sends = new ArrayList<>();
                // send WORKERS + EXTRA messages; with synchronous=true only WORKERS can be in flight
                for (int i = 0; i < WORKERS + EXTRA; i++) {
                    final int idx = i;
                    sends.add(CompletableFuture.runAsync(
                            () -> template.sendBody("vertx-websocket:localhost:" + serverPort + "/test", "message-" + idx),
                            senders));
                }

                // exactly WORKERS messages must arrive at the async step (the extra one is queued)
                assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                        "Not all worker-bounded messages arrived at the async step");
                // give a moment for any extra message to erroneously arrive
                Awaitility.await().during(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            synchronized (waitingLock) {
                                assertEquals(WORKERS, waiting.size(),
                                        "Worker pool did not bound messages in flight");
                            }
                        });

                // complete waiting callbacks to unblock and let the extra message through
                List<AsyncCallback> toComplete;
                synchronized (waitingLock) {
                    toComplete = new ArrayList<>(waiting);
                    waiting.clear();
                }
                // reset latch for the extra message
                arrivedLatch = new CountDownLatch(EXTRA);
                for (AsyncCallback callback : toComplete) {
                    callback.done(false);
                }

                // the extra message should now proceed
                assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                        "Extra message did not arrive after workers freed");

                // complete remaining
                synchronized (waitingLock) {
                    for (AsyncCallback callback : waiting) {
                        callback.done(false);
                    }
                }

                // all sends must complete
                for (CompletableFuture<Void> send : sends) {
                    send.get(10, TimeUnit.SECONDS);
                }
            } finally {
                template.stop();
                senders.shutdown();
            }
        }
    }
}
