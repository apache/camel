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
package org.apache.camel.component.platform.http.vertx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code synchronous} option: with {@code synchronous=true} a worker thread runs the route to its end, also when
 * the route waits for an asynchronous step, so the worker pool bounds the requests in flight. With
 * {@code synchronous=false} (the default) the worker thread is free once the route waits for the asynchronous step. See
 * {@link VertxPlatformHttpSharedVertxWorkerPoolTest} for a route that needs the asynchronous mode.
 */
class VertxPlatformHttpSynchronousTest {

    private static final int WORKERS = 2;
    private static final int REQUESTS = 4;

    private Vertx vertx;
    // the server listens on a port the OS picks when it binds, so no other socket can take it in between
    private VertxPlatformHttpServer server;
    // the requests that reached the asynchronous step and wait for it to complete
    private final Queue<AsyncCallback> waiting = new ConcurrentLinkedQueue<>();
    private final AtomicInteger arrived = new AtomicInteger();
    private final AtomicInteger notOnWorker = new AtomicInteger();
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
    void synchronousEndpointOptionBoundsRequestsInFlightByWorkerPool() throws Exception {
        assertWorkerPoolBoundsRequestsInFlight(runRoute("?synchronous=true", false));
    }

    @Test
    void synchronousComponentOptionBoundsRequestsInFlightByWorkerPool() throws Exception {
        assertWorkerPoolBoundsRequestsInFlight(runRoute("", true));
    }

    @Test
    void asynchronousDoesNotHoldWorkerThreads() throws Exception {
        assertWorkerThreadsAreNotHeld(runRoute("?synchronous=false", false));
    }

    @Test
    void endpointOptionOverridesComponentOption() throws Exception {
        assertWorkerThreadsAreNotHeld(runRoute("?synchronous=false", true));
    }

    private void assertWorkerThreadsAreNotHeld(CamelContext context) throws Exception {
        try (context) {
            List<CompletableFuture<HttpResponse<String>>> responses = sendRequests();

            // every request reaches the asynchronous step, although there are only WORKERS worker threads
            assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                    "Only " + arrived.get() + " of " + REQUESTS + " requests reached the asynchronous step");
            assertEquals(REQUESTS, waiting.size());

            completeWaiting(REQUESTS);
            assertResponses(responses);
            assertEquals(0, notOnWorker.get(), "The route did not start on a Vert.x worker thread");
        }
    }

    private void assertWorkerPoolBoundsRequestsInFlight(CamelContext context) throws Exception {
        try (context) {
            List<CompletableFuture<HttpResponse<String>>> responses = sendRequests();

            // each worker thread waits until its route completes, so only WORKERS requests reach the asynchronous step
            assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                    "Only " + arrived.get() + " requests reached the asynchronous step");
            await().during(500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(WORKERS, arrived.get(),
                            "The worker pool did not bound the requests in flight"));
            assertEquals(WORKERS, waiting.size());

            // once the first requests complete, the worker threads route the next ones
            completeWaiting(REQUESTS);
            assertResponses(responses);
            assertEquals(REQUESTS, arrived.get());
            assertEquals(0, notOnWorker.get(), "The route did not start on a Vert.x worker thread");
        }
    }

    private CamelContext runRoute(String query, boolean synchronousComponent) throws Exception {
        // in synchronous mode only WORKERS requests arrive before some complete
        arrivedLatch = new CountDownLatch(query.contains("synchronous=false") ? REQUESTS : WORKERS);

        CamelContext context = new DefaultCamelContext();
        VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
        configuration.setBindPort(0);

        context.getShutdownStrategy().setTimeout(5);
        context.getRegistry().bind("vertx", vertx);
        server = new VertxPlatformHttpServer(configuration);
        context.addService(server);
        if (synchronousComponent) {
            context.getComponent("platform-http", PlatformHttpComponent.class).setSynchronous(true);
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/inflight" + query)
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                if (!Thread.currentThread().getName().startsWith("vert.x-worker-thread")) {
                                    notOnWorker.incrementAndGet();
                                }
                                // the step completes later, on another thread
                                waiting.add(callback);
                                arrived.incrementAndGet();
                                arrivedLatch.countDown();
                                return false;
                            }
                        })
                        .setBody().constant("Done");
            }
        });
        context.start();
        return context;
    }

    private List<CompletableFuture<HttpResponse<String>>> sendRequests() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request
                = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getServer().actualPort() + "/inflight"))
                        .timeout(Duration.ofSeconds(20)).GET().build();
        List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            responses.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        return responses;
    }

    private void completeWaiting(int count) {
        for (int i = 0; i < count; i++) {
            // the step completes on this thread, not on a Vert.x thread
            AsyncCallback callback = await().atMost(15, TimeUnit.SECONDS).until(waiting::poll, Objects::nonNull);
            callback.done(false);
        }
    }

    private static void assertResponses(List<CompletableFuture<HttpResponse<String>>> responses) throws Exception {
        for (CompletableFuture<HttpResponse<String>> response : responses) {
            HttpResponse<String> r = response.get(15, TimeUnit.SECONDS);
            assertEquals(200, r.statusCode());
            assertEquals("Done", r.body());
        }
    }
}
