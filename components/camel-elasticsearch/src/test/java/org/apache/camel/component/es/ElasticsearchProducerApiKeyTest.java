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
package org.apache.camel.component.es;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticsearchProducerApiKeyTest {

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
            ElasticsearchComponent component = component(context);
            component.setApiKey("VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==");

            Response response = send(context);

            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("ApiKey VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==", authorizations.get("node"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    @Test
    void encodesAnApiKeyGivenAsIdAndKey() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchComponent component = component(context);
            component.setApiKey("my-id:my-key");

            send(context);

            String encoded = Base64.getEncoder().encodeToString("my-id:my-key".getBytes(StandardCharsets.UTF_8));
            assertEquals("ApiKey " + encoded, authorizations.get("node"));
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
            ElasticsearchComponent component = component(context);
            component.setApiKey("my-id:my-key");
            component.setUser("elastic");
            component.setPassword("secret");

            ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                    () -> context.getEndpoint("elasticsearch:test-cluster"));
            IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals(
                    "Both apiKey and user and password are configured on the elasticsearch component: use either apiKey, or"
                         + " user and password",
                    cause.getMessage());
        }
    }

    @Test
    void usesTheApiKeyWhenTheUserHasNoPassword() throws Exception {
        // inside a Kamelet an optional {{?user}} that is not set can resolve from the USER environment variable
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchComponent component = component(context);
            component.setApiKey("my-id:my-key");
            component.setUser("elastic");

            send(context);

            assertEquals("ApiKey " + Base64.getEncoder().encodeToString("my-id:my-key".getBytes(StandardCharsets.UTF_8)),
                    authorizations.get("node"));
            assertEquals(NO_HEADER, authorizations.get("other"));
        }
    }

    @Test
    void ignoresAnEmptyApiKey() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchComponent component = component(context);
            component.setApiKey("");
            component.setUser("elastic");
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

    private ElasticsearchComponent component(CamelContext context) {
        ElasticsearchComponent component = new ElasticsearchComponent(context);
        component.setHostAddresses("127.0.0.1:" + node.getAddress().getPort());
        context.addComponent("elasticsearch", component);
        return component;
    }

    private static Response send(CamelContext context) throws Exception {
        ElasticsearchEndpoint endpoint = context.getEndpoint("elasticsearch:test-cluster", ElasticsearchEndpoint.class);
        try (RestClient client = new ElasticsearchProducer(endpoint, endpoint.getConfiguration()).createClient()) {
            return client.performRequest(new Request("GET", "/"));
        }
    }
}
