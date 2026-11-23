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
package org.apache.camel.component.knative.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.component.knative.KnativeComponent;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeEnvironment;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpEngine;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEndpoint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code synchronous} option on the knative-http consumer: with {@code synchronous=true} the worker thread runs the
 * route until it ends, holding the thread even during async steps. This bounds the requests in flight by the worker
 * pool size.
 */
class KnativeHttpSynchronousTest {

    private static final int WORKERS = 2;
    private static final int EXTRA = 1;

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private Vertx vertx;
    private final List<AsyncCallback> waiting = new ArrayList<>();
    private final Object waitingLock = new Object();
    private CountDownLatch arrivedLatch;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(WORKERS));
        arrivedLatch = new CountDownLatch(WORKERS);
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    private CamelContext buildContext(boolean synchronous) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getShutdownStrategy().setTimeout(5);
        context.getRegistry().bind("vertx", vertx);

        VertxPlatformHttpServerConfiguration cfg = new VertxPlatformHttpServerConfiguration();
        cfg.setBindPort(port.getPort());
        context.addService(new VertxPlatformHttpServer(cfg));

        PlatformHttpComponent platformHttp = new PlatformHttpComponent(context);
        platformHttp.setEngine(new VertxPlatformHttpEngine());
        context.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME, platformHttp);

        KnativeComponent knative = new KnativeComponent();
        knative.setCloudEventsSpecVersion(CloudEvents.v1_0.version());
        knative.getConfiguration().setSynchronous(synchronous);
        knative.setEnvironment(new KnativeEnvironment(
                List.of(
                        sourceEndpoint("test", Map.of(
                                Knative.SERVICE_META_PATH, "/",
                                Knative.KNATIVE_CLOUD_EVENT_TYPE, "test.type",
                                Knative.CONTENT_TYPE, "text/plain")))));
        knative.setConsumerFactory(new KnativeHttpConsumerFactory(context) {
            @Override
            protected void doBuild() throws Exception {
                super.doBuild();
                this.setRouter(VertxPlatformHttpRouter.lookup(context,
                        VertxPlatformHttpRouter.getRouterNameFromPort(port.getPort())));
            }
        });
        context.addComponent("knative", knative);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("knative:endpoint/test")
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
        return context;
    }

    @Test
    void synchronousOptionBoundsRequestsInFlightByWorkerPool() throws Exception {
        try (CamelContext context = buildContext(true)) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port.getPort() + "/"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "text/plain")
                    .header("ce-specversion", "1.0")
                    .header("ce-type", "test.type")
                    .header("ce-id", "test-id")
                    .header("ce-source", "/test")
                    .POST(HttpRequest.BodyPublishers.ofString("hello"))
                    .build();

            // send WORKERS + EXTRA requests; synchronous=true means at most WORKERS in flight
            List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
            for (int i = 0; i < WORKERS + EXTRA; i++) {
                responses.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            }

            // WORKERS arrive at the async step, the extra one is blocked
            assertThat(arrivedLatch.await(10, TimeUnit.SECONDS))
                    .as("Timed out waiting for worker-bounded requests to arrive")
                    .isTrue();
            Awaitility.await().during(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(waiting).hasSize(WORKERS));

            // complete waiting callbacks
            List<AsyncCallback> toComplete;
            synchronized (waitingLock) {
                toComplete = new ArrayList<>(waiting);
                waiting.clear();
            }
            arrivedLatch = new CountDownLatch(EXTRA);
            for (AsyncCallback callback : toComplete) {
                callback.done(false);
            }

            // extra request now proceeds
            assertThat(arrivedLatch.await(10, TimeUnit.SECONDS))
                    .as("Extra request did not arrive after workers freed")
                    .isTrue();

            synchronized (waitingLock) {
                for (AsyncCallback callback : waiting) {
                    callback.done(false);
                }
            }

            for (CompletableFuture<HttpResponse<String>> response : responses) {
                HttpResponse<String> r = response.get(10, TimeUnit.SECONDS);
                assertThat(r.statusCode()).isEqualTo(200);
            }
        }
    }
}
