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
package org.apache.camel.component.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The http options given on a rest endpoint uri are applied to the http producer and are not sent as query parameters,
 * however the http component is chosen as the producer component of the rest endpoint.
 */
public class HttpRestProducerQueryParametersTest {

    private static final String OPTIONS = "authMethod=Basic&authenticationPreemptive=true"
                                          + "&authUsername=ann&authPassword=RAW(secret)&page=2";

    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    private HttpServer server;
    private DefaultCamelContext context;

    @BeforeEach
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/items", this::handle);
        server.start();

        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void stop() {
        context.stop();
        server.stop(0);
    }

    @Test
    public void shouldNotSendHttpOptionsWithProducerComponentOfRestConfiguration() {
        context.getRestConfiguration().setProducerComponent("http");

        send("rest:get:api/items?host=" + host() + "&" + OPTIONS);

        assertThat(query.get()).isEqualTo("page=2");
        assertThat(authorization.get()).isEqualTo("Basic YW5uOnNlY3JldA==");
    }

    @Test
    public void shouldNotSendHttpOptionsWithAutoDetectedProducerComponent() {
        send("rest:get:api/items?host=" + host() + "&" + OPTIONS);

        assertThat(query.get()).isEqualTo("page=2");
        assertThat(authorization.get()).isEqualTo("Basic YW5uOnNlY3JldA==");
    }

    @Test
    public void shouldNotSendHttpOptionsWithProducerComponentName() {
        send("rest:get:api/items?host=" + host() + "&producerComponentName=http&" + OPTIONS);

        assertThat(query.get()).isEqualTo("page=2");
        assertThat(authorization.get()).isEqualTo("Basic YW5uOnNlY3JldA==");
    }

    private String host() {
        return "localhost:" + server.getAddress().getPort();
    }

    private void send(String uri) {
        ProducerTemplate template = context.createProducerTemplate();
        Exchange exchange = template.request(uri, null);
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(200);
    }

    private void handle(HttpExchange exchange) throws IOException {
        query.set(exchange.getRequestURI().getRawQuery());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
