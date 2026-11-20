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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A platform-http route that calls vertx-http, with one Vert.x for both (a Vert.x in the registry, as in Camel
 * Quarkus): the route must not hold a worker thread of the Vert.x until it completes, as vertx-http completes its
 * exchange on a worker thread of the same Vert.x. With as many requests in flight as worker threads, no request would
 * complete.
 */
class VertxPlatformHttpSharedVertxWorkerPoolTest {

    private static final int WORKERS = 2;

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private Vertx vertx;
    private HttpServer backend;
    private ExecutorService backendThreads;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(WORKERS));
        backend = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // the backend answers once every request has arrived, so each request holds a worker thread meanwhile when the
        // route does not give it back
        CountDownLatch arrived = new CountDownLatch(WORKERS);
        backend.createContext("/backend", exchange -> {
            arrived.countDown();
            try {
                arrived.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "Hello from backend".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        backendThreads = Executors.newCachedThreadPool();
        backend.setExecutor(backendThreads);
        backend.start();
    }

    @AfterEach
    void tearDown() {
        backend.stop(0);
        backendThreads.shutdownNow();
        vertx.close();
    }

    @Test
    void requestsAsManyAsWorkerThreadsComplete() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
            configuration.setBindPort(port.getPort());

            context.getShutdownStrategy().setTimeout(5);
            context.getRegistry().bind("vertx", vertx);
            context.addService(new VertxPlatformHttpServer(configuration));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/proxy")
                            // call the backend uri, not the uri of the request
                            .removeHeaders("CamelHttp*")
                            .to("vertx-http:http://localhost:" + backend.getAddress().getPort() + "/backend");
                }
            });
            context.start();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port.getPort() + "/proxy"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
            for (int i = 0; i < WORKERS; i++) {
                responses.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            }
            for (CompletableFuture<HttpResponse<String>> response : responses) {
                HttpResponse<String> r = response.get(15, TimeUnit.SECONDS);
                assertEquals(200, r.statusCode());
                assertEquals("Hello from backend", r.body());
            }
        }
    }
}
