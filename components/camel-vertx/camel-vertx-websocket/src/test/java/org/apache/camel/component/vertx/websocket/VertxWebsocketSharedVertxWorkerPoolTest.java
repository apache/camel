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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vertx-websocket consumer route that includes an asynchronous step must not hold the Vert.x worker thread while
 * waiting for the asynchronous step to complete. With as many messages in flight as worker threads and all workers
 * held, no message would ever complete.
 */
class VertxWebsocketSharedVertxWorkerPoolTest {

    private static final int WORKERS = 2;

    private Vertx vertx;
    // callbacks waiting to be completed at the asynchronous step
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
    void messagesAsManyAsWorkerThreadsComplete() throws Exception {
        arrivedLatch = new CountDownLatch(WORKERS);
        try (CamelContext context = new DefaultCamelContext()) {
            context.getShutdownStrategy().setTimeout(5);
            context.getRegistry().bind("vertx", vertx);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // an asynchronous step that waits until WORKERS messages have arrived;
                    // if the route held the worker thread, with WORKERS messages in flight
                    // no message would ever reach this step
                    from("vertx-websocket:localhost:0/test")
                            .process(new AsyncProcessorSupport() {
                                @Override
                                public boolean process(Exchange exchange, AsyncCallback callback) {
                                    synchronized (waitingLock) {
                                        waiting.add(callback);
                                        arrivedLatch.countDown();
                                    }
                                    return false; // async: callback not called yet
                                }
                            });
                }
            });
            context.start();

            VertxWebsocketComponent component = context.getComponent("vertx-websocket", VertxWebsocketComponent.class);
            int serverPort = component.getVertxHostRegistry().values().iterator().next().getPort();

            // send WORKERS messages concurrently via the shared Vert.x producer
            ExecutorService senders = Executors.newFixedThreadPool(WORKERS);
            ProducerTemplate template = context.createProducerTemplate();
            try {
                List<CompletableFuture<Void>> sends = new ArrayList<>();
                for (int i = 0; i < WORKERS; i++) {
                    final int idx = i;
                    sends.add(CompletableFuture.runAsync(
                            () -> template.sendBody("vertx-websocket:localhost:" + serverPort + "/test", "message-" + idx),
                            senders));
                }

                // wait until all messages have arrived at the async step — deadlocks if workers are held
                assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                        "Timed out waiting for all messages to arrive at the async step");
                assertEquals(WORKERS, waiting.size(), "All messages should have reached the async step");

                // complete all waiting callbacks on a Vert.x worker — fails with the old code
                for (AsyncCallback callback : waiting) {
                    vertx.executeBlocking(() -> {
                        callback.done(false);
                        return null;
                    }, false);
                }

                // verify all sends complete without timeout
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
