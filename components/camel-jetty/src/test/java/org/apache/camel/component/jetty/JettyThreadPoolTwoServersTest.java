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
package org.apache.camel.component.jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With minThreads / maxThreads on the component, each Jetty server has its own thread pool: stopping the route of one
 * server does not stop the thread pool of another one, and each thread pool is stopped with its own server.
 */
class JettyThreadPoolTwoServersTest extends BaseJettyTest {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void stoppingOneServerKeepsTheOtherServing() throws Exception {
        assertEquals("a", get(getPort(), "a"));
        assertEquals("b", get(getPort2(), "b"));

        context.getRouteController().stopRoute("a");

        assertEquals("b", get(getPort2(), "b"), "the server of route b no longer answers after route a stopped");
    }

    @Test
    void stoppingBothServersStopsBothThreadPools() throws Exception {
        assertEquals("a", get(getPort(), "a"));
        assertEquals("b", get(getPort2(), "b"));
        // the thread pools of the two servers of this test (other test classes may run Jetty servers at the same time)
        List<String> pools = List.of(threadPoolName(getPort()), threadPoolName(getPort2()));

        context.getRouteController().stopRoute("b");
        context.getRouteController().stopRoute("a");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(0, countThreads(pools), "threads of a Jetty server thread pool left running"));
    }

    private static String threadPoolName(int port) {
        return ((QueuedThreadPool) JettyHttpComponent.CONNECTORS.get("http:localhost:" + port).server.getThreadPool())
                .getName();
    }

    private static long countThreads(List<String> pools) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && pools.stream().anyMatch(pool -> t.getName().startsWith(pool))).count();
    }

    private String get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + path))
                .timeout(Duration.ofSeconds(5)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JettyHttpComponent jetty = (JettyHttpComponent) context.getComponent("jetty");
                jetty.setMinThreads(4);
                jetty.setMaxThreads(10);

                from("jetty://http://localhost:{{port}}/a").routeId("a").setBody(constant("a"));
                from("jetty://http://localhost:{{port2}}/b").routeId("b").setBody(constant("b"));
            }
        };
    }
}
