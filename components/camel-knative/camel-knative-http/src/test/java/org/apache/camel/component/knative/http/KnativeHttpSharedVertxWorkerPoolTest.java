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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.knative.KnativeEnvironmentSupport.sourceEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A knative-http consumer route that includes an asynchronous step must not hold the Vert.x worker thread while waiting
 * for the asynchronous step to complete. With as many requests in flight as worker threads and all workers held, no
 * request would ever complete.
 */
class KnativeHttpSharedVertxWorkerPoolTest {

    private static final int WORKERS = 2;

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private Vertx vertx;
    // callbacks waiting to be completed at the asynchronous step
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

    @Test
    void requestsAsManyAsWorkerThreadsComplete() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getShutdownStrategy().setTimeout(5);
            context.getRegistry().bind("vertx", vertx);

            // set up the platform-http server on the shared Vert.x
            VertxPlatformHttpServerConfiguration cfg = new VertxPlatformHttpServerConfiguration();
            cfg.setBindPort(port.getPort());
            context.addService(new VertxPlatformHttpServer(cfg));

            PlatformHttpComponent platformHttp = new PlatformHttpComponent(context);
            platformHttp.setEngine(new VertxPlatformHttpEngine());
            context.getRegistry().bind(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME, platformHttp);

            // set up the knative component with the shared router and vertx
            KnativeComponent knative = new KnativeComponent();
            knative.setCloudEventsSpecVersion(CloudEvents.v1_0.version());
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
                    // an asynchronous step that holds until WORKERS requests have all arrived;
                    // if the route held the worker thread, with WORKERS requests in flight
                    // no request would ever reach this step
                    from("knative:endpoint/test")
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

            List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
            for (int i = 0; i < WORKERS; i++) {
                responses.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            }

            // wait until all requests have arrived at the async step — deadlocks if workers are held
            assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                    "Timed out waiting for all requests to arrive at the async step");
            assertThat(waiting).hasSize(WORKERS);

            // complete all waiting callbacks on a Vert.x worker — fails with the old code
            for (AsyncCallback callback : waiting) {
                vertx.executeBlocking(() -> {
                    callback.done(false);
                    return null;
                }, false);
            }

            for (CompletableFuture<HttpResponse<String>> response : responses) {
                HttpResponse<String> r = response.get(10, TimeUnit.SECONDS);
                // 200: the route replied with the request body (knative reply=true echoes the body back)
                assertThat(r.statusCode()).isEqualTo(200);
            }
        }
    }
}
