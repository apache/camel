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
package org.apache.camel.component.elasticsearch.rest.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The producer authenticates to the node it is configured with, through the client it creates itself.
 */
class ElasticsearchRestClientBasicAuthenticationTest {

    private static final String AUTHORIZATION
            = "Basic " + Base64.getEncoder().encodeToString("elastic:secret".getBytes(StandardCharsets.UTF_8));

    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private HttpServer node;

    @BeforeEach
    void startNode() throws IOException {
        node = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        node.createContext("/", this::handle);
        node.start();
    }

    @AfterEach
    void stopNode() {
        node.stop(0);
    }

    @Test
    void sendsTheConfiguredCredentialsToTheNode() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
            component.setHostAddressesList("127.0.0.1:" + node.getAddress().getPort());
            component.setUser("elastic");
            component.setPassword("secret");
            context.addComponent("elasticsearch-rest-client", component);
            context.start();

            Object acknowledged = context.createProducerTemplate()
                    .requestBody("elasticsearch-rest-client:camel?operation=CREATE_INDEX&indexName=camel", (Object) null);

            assertEquals(Boolean.TRUE, acknowledged);
            assertEquals(List.of(AUTHORIZATION), authorizations);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        if (AUTHORIZATION.equals(authorization)) {
            byte[] body = "{\"acknowledged\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"camel\"");
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        }
    }
}
