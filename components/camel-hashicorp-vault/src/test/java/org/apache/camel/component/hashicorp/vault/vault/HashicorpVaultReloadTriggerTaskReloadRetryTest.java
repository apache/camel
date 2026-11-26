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
package org.apache.camel.component.hashicorp.vault.vault;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reload triggered by a new version of a secret that fails (here a consumer that cannot start once) is tried again by
 * the next run of the refresh task, and the routes end up started with the new version of the secret. Runs against a
 * minimal fake of the HashiCorp Vault KV v2 HTTP API.
 */
class HashicorpVaultReloadTriggerTaskReloadRetryTest {

    private static HttpServer server;
    // "<engine>/<key>" -> current version
    private static final Map<String, Integer> VERSIONS = new ConcurrentHashMap<>();

    // consumer starts that fail, as when the remote system of the route is briefly unreachable
    private final AtomicInteger failStarts = new AtomicInteger();
    // the password of each consumer that started
    private final List<String> startedWith = new CopyOnWriteArrayList<>();

    private DefaultCamelContext context;
    private DefaultContextReloadStrategy reload;
    private HashicorpVaultReloadTriggerTask task;

    @BeforeAll
    static void startFakeVault() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/", HashicorpVaultReloadTriggerTaskReloadRetryTest::handle);
        server.start();
    }

    @AfterAll
    static void stopFakeVault() {
        server.stop(0);
    }

    private static void handle(HttpExchange http) throws IOException {
        // /v1/<engine>/<data|metadata>/<key>
        String[] parts = http.getRequestURI().getPath().substring("/v1/".length()).split("/", 3);
        String body = null;
        if (parts.length == 3) {
            Integer version = VERSIONS.get(parts[0] + "/" + parts[2]);
            if ("metadata".equals(parts[1]) && version != null) {
                body = "{\"data\":{\"current_version\":" + version + "}}";
            } else if ("data".equals(parts[1]) && version != null) {
                body = "{\"data\":{\"data\":{\"password\":\"pw-" + version + "\"},\"metadata\":{\"version\":" + version
                       + "}}}";
            }
        }
        int status = body != null ? 200 : 404;
        byte[] bytes = (body != null ? body : "{\"errors\":[]}").getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json");
        http.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = http.getResponseBody()) {
            os.write(bytes);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        VERSIONS.put("secret/api", 1);
        context = new DefaultCamelContext();
        HashicorpVaultConfiguration hashicorp = new HashicorpVaultConfiguration();
        hashicorp.setToken("test-token");
        hashicorp.setHost("localhost");
        hashicorp.setPort(String.valueOf(server.getAddress().getPort()));
        hashicorp.setScheme("http");
        context.getVaultConfiguration().setHashicorpVaultConfiguration(hashicorp);
        context.addComponent("flaky", new FlakyComponent());
        reload = new DefaultContextReloadStrategy();
        context.addService(reload);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:in?password={{hashicorp:secret:api#password}}").routeId("secured").log("${body}");
            }
        });
        context.start();

        task = new HashicorpVaultReloadTriggerTask();
        task.setCamelContext(context);
        task.start();
        task.run();
        assertEquals(List.of("pw-1"), startedWith);
        assertEquals(0, reload.getReloadCounter() + reload.getFailedCounter(), "no new version yet, no reload");
    }

    @AfterEach
    void tearDown() {
        task.stop();
        context.stop();
        VERSIONS.clear();
    }

    private ServiceStatus routeStatus() {
        return context.getRouteController().getRouteStatus("secured");
    }

    @Test
    void failedReloadIsTriedAgainByTheNextRun() {
        VERSIONS.put("secret/api", 2);
        failStarts.set(1);
        task.run();
        assertEquals(1, reload.getFailedCounter(), "the reload failed");
        assertEquals(ServiceStatus.Stopped, routeStatus());

        task.run();
        assertEquals(1, reload.getReloadCounter(), "the next run must reload again");
        assertEquals(ServiceStatus.Started, routeStatus());
        assertEquals(List.of("pw-1", "pw-2"), startedWith, "the route runs with the new version of the secret");

        task.run();
        assertEquals(1, reload.getReloadCounter(), "the new version is marked as seen once the reload succeeded");
        assertEquals(1, reload.getFailedCounter());
    }

    @Test
    void failedReloadIsTriedAtMostMaxAttemptsTimes() {
        VERSIONS.put("secret/api", 2);
        failStarts.set(Integer.MAX_VALUE);
        for (int i = 0; i < HashicorpVaultReloadTriggerTask.MAX_RELOAD_ATTEMPTS + 3; i++) {
            task.run();
        }
        assertEquals(HashicorpVaultReloadTriggerTask.MAX_RELOAD_ATTEMPTS, reload.getFailedCounter(),
                "one reload per run, until the new version is marked as seen");

        failStarts.set(0);
        task.run();
        assertEquals(0, reload.getReloadCounter(), "the new version was marked as seen");

        // the next version reloads again
        VERSIONS.put("secret/api", 3);
        task.run();
        assertEquals(1, reload.getReloadCounter());
        assertEquals(ServiceStatus.Started, routeStatus());
        assertEquals("pw-3", startedWith.get(startedWith.size() - 1));
    }

    private final class FlakyComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            String password = (String) parameters.remove("password");
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() throws Exception {
                            if (failStarts.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                                throw new IllegalStateException("remote system unreachable");
                            }
                            startedWith.add(password);
                            super.doStart();
                        }
                    };
                }
            };
        }
    }
}
