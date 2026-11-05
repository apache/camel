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
package org.apache.camel.component.azure.eventgrid.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * A webhook subscriber of an Event Grid topic of Floci AZ: an HTTP server of the test, which the container of Floci AZ
 * reaches at {@code host.testcontainers.internal}. It answers the validation handshake of the CloudEvents schema and
 * keeps the events delivered to it.
 * <p>
 * Register it before the service of Floci AZ: a container has the {@code host.testcontainers.internal} host only when a
 * port of the host was exposed before it started.
 */
class EventGridSubscriber implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(EventGridSubscriber.class);
    private static final String PATH = "/events";

    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(PATH, this::handle);
        server.start();

        Testcontainers.exposeHostPorts(server.getAddress().getPort());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * The URL of the webhook for the container of Floci AZ
     */
    String endpointUrl() {
        return "http://" + GenericContainer.INTERNAL_HOST_HOSTNAME + ":" + server.getAddress().getPort() + PATH;
    }

    /**
     * The events received with a subject, as their JSON
     */
    List<Map<String, Object>> events(String subject) {
        return events.stream().filter(event -> subject.equals(event.get("subject"))).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            switch (exchange.getRequestMethod()) {
                case "OPTIONS" -> allowOrigin(exchange);
                case "POST" -> receive(exchange);
                default -> exchange.sendResponseHeaders(405, -1);
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * The validation handshake of the CloudEvents schema (abuse protection): Event Grid delivers to a webhook that
     * allows the origin of the request
     */
    private static void allowOrigin(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("WebHook-Request-Origin");
        if (origin == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        LOG.info("Allowing the origin {} of the validation handshake", origin);
        exchange.getResponseHeaders().set("WebHook-Allowed-Origin", origin);
        exchange.sendResponseHeaders(200, -1);
    }

    /**
     * A delivery: a JSON array of events in the CloudEvents schema
     */
    @SuppressWarnings("unchecked")
    private void receive(HttpExchange exchange) throws IOException {
        try (JsonReader reader = JsonProviders.createReader(exchange.getRequestBody().readAllBytes())) {
            List<Map<String, Object>> delivered = (List<Map<String, Object>>) reader.readUntyped();
            LOG.info("Received {}", delivered);
            events.addAll(delivered);
        }
        exchange.sendResponseHeaders(200, -1);
    }
}
