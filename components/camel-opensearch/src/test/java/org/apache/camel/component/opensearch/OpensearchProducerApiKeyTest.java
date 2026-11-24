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
package org.apache.camel.component.opensearch;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpensearchProducerApiKeyTest {

    private static final String NO_HEADER = "<none>";

    private final Map<String, String> authorizations = new ConcurrentHashMap<>();
    private HttpServer node;
    private HttpServer other;

    @BeforeEach
    void startServers() throws Exception {
        other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/", exchange -> {
            record("other", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        other.start();

        node = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        node.createContext("/", exchange -> {
            record("node", exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        node.start();
    }

    @AfterEach
    void stopServers() {
        if (node != null) {
            node.stop(0);
        }
        if (other != null) {
            other.stop(0);
        }
    }

    @Test
    void sendsTheApiKeyToTheNodesOnlyAndNotToARedirectTarget() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchComponent component = component(context);
            component.setApiKey("os_3jVHq8X2mK9pLwR5tN7bYcD1fA4eZ6uS");

            Response response = send(context);

            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("ApiKey os_3jVHq8X2mK9pLwR5tN7bYcD1fA4eZ6uS", authorizations.get("node"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    @Test
    void sendsNoAuthorizationWithoutAnApiKey() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            component(context);

            send(context);

            assertEquals(NO_HEADER, authorizations.get("node"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    @Test
    void failsWhenApiKeyAndUserAndPasswordAreSet() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchComponent component = component(context);
            component.setApiKey("my-id:my-key");
            component.setUser("admin");
            component.setPassword("secret");

            ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                    () -> context.getEndpoint("opensearch:test-cluster"));
            IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Both apiKey and user and password are configured on the opensearch component: use either apiKey, or"
                         + " user and password",
                    cause.getMessage());
        }
    }

    @Test
    void usesTheApiKeyWhenTheUserHasNoPassword() throws Exception {
        // inside a Kamelet an optional {{?user}} that is not set can resolve from the USER environment variable
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchComponent component = component(context);
            component.setApiKey("os_3jVHq8X2mK9pLwR5tN7bYcD1fA4eZ6uS");
            component.setUser("admin");

            send(context);

            assertEquals("ApiKey os_3jVHq8X2mK9pLwR5tN7bYcD1fA4eZ6uS", authorizations.get("node"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    @Test
    void ignoresAnEmptyApiKey() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchComponent component = component(context);
            component.setApiKey("");
            component.setUser("admin");
            component.setPassword("secret");

            send(context);

            // user and password are used instead, which the client may send before it is challenged
            assertFalse(authorizations.getOrDefault("node", NO_HEADER).startsWith("ApiKey"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    private void record(String server, String authorization) {
        authorizations.put(server, authorization != null ? authorization : NO_HEADER);
    }

    private OpensearchComponent component(CamelContext context) {
        OpensearchComponent component = new OpensearchComponent(context);
        component.setHostAddresses("127.0.0.1:" + node.getAddress().getPort());
        context.addComponent("opensearch", component);
        return component;
    }

    private static Response send(CamelContext context) throws Exception {
        OpensearchEndpoint endpoint = context.getEndpoint("opensearch:test-cluster", OpensearchEndpoint.class);
        try (RestClient client = new OpensearchProducer(endpoint, endpoint.getConfiguration()).createClient()) {
            return client.performRequest(new Request("GET", "/"));
        }
    }
}
