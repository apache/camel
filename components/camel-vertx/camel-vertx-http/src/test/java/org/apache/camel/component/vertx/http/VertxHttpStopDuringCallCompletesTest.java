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
package org.apache.camel.component.vertx.http;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.internal.VertxBootstrap;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CamelContext is stopped while the producer waits for the answer of the server, or the worker pool of the Vert.x
 * refuses the task that handles the answer: the exchange must complete (with an exception), the caller must not wait
 * for ever.
 */
class VertxHttpStopDuringCallCompletesTest {

    private final CountDownLatch received = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private ExecutorService serverThreads;
    private HttpServer server;
    private CamelContext context;
    private Vertx vertx;

    @BeforeEach
    void startServer() throws Exception {
        serverThreads = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            received.countDown();
            try {
                // answers only after the test, so the call is in flight when the CamelContext stops
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.setExecutor(serverThreads);
        server.start();
    }

    @AfterEach
    void stopAll() throws Exception {
        release.countDown();
        if (context != null) {
            context.stop();
        }
        if (vertx != null) {
            vertx.close().await(10, TimeUnit.SECONDS);
        }
        server.stop(0);
        serverThreads.shutdownNow();
    }

    @Test
    void exchangeCompletesWhenTheCamelContextStopsDuringTheCall() throws Exception {
        CompletableFuture<Object> reply = sendAndStopDuringTheCall();

        ExecutionException e = assertThrows(ExecutionException.class, () -> reply.get(20, TimeUnit.SECONDS));
        assertInstanceOf(HttpClosedException.class, ObjectHelper.getException(HttpClosedException.class, e),
                "the exchange must fail as the connection was closed: " + e);
    }

    @Test
    void exchangeCompletesWhenTheCamelContextStopsDuringTheCallWithAVertxNotManagedByCamel() throws Exception {
        // a Vert.x of the application is not closed by Camel: only the web client of the endpoint is closed
        vertx = Vertx.vertx();
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);

        CompletableFuture<Object> reply = sendAndStopDuringTheCall();

        ExecutionException e = assertThrows(ExecutionException.class, () -> reply.get(20, TimeUnit.SECONDS));
        assertInstanceOf(HttpClosedException.class, ObjectHelper.getException(HttpClosedException.class, e),
                "the exchange must fail as the connection was closed: " + e);
    }

    @Test
    void exchangeCompletesWhenTheWorkerPoolRefusesTheResponseTask() throws Exception {
        // a Vert.x whose worker pool the test can shut down, as when a Vert.x is closing while the answer arrives; the
        // worker pool is told apart by its size, the internal blocking pool stays up so the Vert.x can still close
        int workerPoolSize = 3;
        List<ExecutorService> workerPools = new CopyOnWriteArrayList<>();
        vertx = VertxBootstrap.create().options(new VertxOptions().setWorkerPoolSize(workerPoolSize))
                .executorServiceFactory((threadFactory, concurrency, maxConcurrency) -> {
                    ExecutorService pool = Executors.newFixedThreadPool(maxConcurrency, threadFactory);
                    if (maxConcurrency == workerPoolSize) {
                        workerPools.add(pool);
                    }
                    return pool;
                })
                .init().vertx();
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);

        CompletableFuture<Object> reply = send();
        workerPools.forEach(ExecutorService::shutdownNow);
        // the server answers now: the task that handles the answer is refused
        release.countDown();

        ExecutionException e = assertThrows(ExecutionException.class, () -> reply.get(20, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class,
                ObjectHelper.getException(RejectedExecutionException.class, e),
                "the exchange must fail as the worker pool refused the task: " + e);
    }

    private CompletableFuture<Object> sendAndStopDuringTheCall() throws Exception {
        CompletableFuture<Object> reply = send();
        context.stop();
        return reply;
    }

    private CompletableFuture<Object> send() throws Exception {
        if (context == null) {
            context = new DefaultCamelContext();
        }
        int port = server.getAddress().getPort();
        // a short graceful shutdown, so the stop goes on while the call is still in flight
        context.getShutdownStrategy().setTimeout(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("vertx-http:http://localhost:" + port + "/slow");
            }
        });
        context.start();

        ProducerTemplate template = context.createProducerTemplate();
        CompletableFuture<Object> reply = template.asyncRequestBody("direct:start", "Hello");
        assertTrue(received.await(10, TimeUnit.SECONDS), "the server did not get the request");
        return reply;
    }
}
