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
package org.apache.camel.component.cxf.jaxrs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suspended CXF-RS consumer (suspended route, graceful shutdown, route policies) must not process new requests.
 */
class CxfRsConsumerSuspendTest extends CamelTestSupport {

    private static final String CONTEXT = "/CxfRsConsumerSuspendTest";
    private static final String URL = "http://localhost:" + CXFTestSupport.getPort1() + CONTEXT
                                      + "/rest/customerservice/customers/123";

    private final AtomicInteger processed = new AtomicInteger();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean block;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxfrs://http://localhost:" + CXFTestSupport.getPort1() + CONTEXT + "/rest"
                     + "?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService")
                        .routeId("rs")
                        .process(exchange -> {
                            if (processed.incrementAndGet() == 1 && block) {
                                entered.countDown();
                                release.await(20, TimeUnit.SECONDS);
                            }
                        })
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void testSuspendedRouteRejectsRequests() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL)).GET().build();
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());

        context.getRouteController().suspendRoute("rs");
        assertEquals(503, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(),
                "A suspended route must answer 503");
        assertEquals(1, processed.get(), "A suspended route must not process a request");

        context.getRouteController().resumeRoute("rs");
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(2, processed.get());
    }

    @Test
    void testGracefulStopCompletesInflightAndRejectsNewRequests() throws Exception {
        block = true;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL)).GET().build();
        // the consumer is asynchronous by default: the continuation of this request resumes after the suspend
        CompletableFuture<HttpResponse<String>> inflight = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(entered.await(20, TimeUnit.SECONDS));

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("rs", 20, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ServiceSupport consumer = (ServiceSupport) context.getRoute("rs").getConsumer();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isSuspendingOrSuspended);

        assertEquals(503, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(),
                "A request received while stopping must be rejected");
        assertEquals(1, processed.get(), "A request received while stopping must not be processed");

        release.countDown();
        assertEquals(200, inflight.get(20, TimeUnit.SECONDS).statusCode());
        stop.get(30, TimeUnit.SECONDS);
    }
}
