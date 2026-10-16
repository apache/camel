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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The producer authenticates to every node it is configured with, through the client it creates itself.
 */
class OpensearchBasicAuthenticationTest {

    private static final String AUTHORIZATION
            = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8));

    private final ClusterNode node1 = new ClusterNode();
    private final ClusterNode node2 = new ClusterNode();

    @BeforeEach
    void startNodes() throws IOException {
        node1.start();
        node2.start();
    }

    @AfterEach
    void stopNodes() {
        node1.stop();
        node2.stop();
    }

    @Test
    void sendsTheConfiguredCredentialsToEveryNode() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            OpensearchComponent component = new OpensearchComponent(context);
            component.setHostAddresses(node1.address() + "," + node2.address());
            component.setUser("admin");
            component.setPassword("secret");
            context.addComponent("opensearch", component);
            context.start();

            // the client sends the requests to the nodes in turn
            ProducerTemplate template = context.createProducerTemplate();
            assertEquals(Boolean.TRUE, template.requestBody("opensearch:test-cluster?operation=Ping", (Object) null));
            assertEquals(Boolean.TRUE, template.requestBody("opensearch:test-cluster?operation=Ping", (Object) null));

            assertEquals(List.of(AUTHORIZATION), node1.authorizations);
            assertEquals(List.of(AUTHORIZATION), node2.authorizations);
        }
    }

    /**
     * A node that answers a ping with the configured credentials, and asks for them otherwise.
     */
    private static final class ClusterNode {

        private final List<String> authorizations = new CopyOnWriteArrayList<>();
        private HttpServer server;

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                authorizations.add(String.valueOf(authorization));
                if (AUTHORIZATION.equals(authorization)) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"camel\"");
                    exchange.sendResponseHeaders(401, -1);
                }
                exchange.close();
            });
            server.start();
        }

        String address() {
            return "127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }
    }
}
