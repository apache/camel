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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The producers that rest components (such as rest-openapi) create for the same url, each with its own options, do not
 * share one endpoint: each producer uses its own login (CAMEL-27234).
 */
public class HttpRestProducerParametersTest {

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
    public void shouldUseTheLoginOfEachProducer() throws Exception {
        Producer anonymous = createProducer(Map.of());
        Producer basic = createProducer(Map.of("authMethod", "Basic", "authUsername", "ann", "authPassword", "secret",
                "authenticationPreemptive", "true"));
        Producer bearer = createProducer(Map.of("authMethod", "Bearer", "authBearerToken", "TOKEN",
                "authenticationPreemptive", "true"));

        assertThat(send(anonymous)).isNull();
        assertThat(send(basic)).isEqualTo("Basic YW5uOnNlY3JldA==");
        assertThat(send(bearer)).isEqualTo("Bearer TOKEN");
    }

    private Producer createProducer(Map<String, Object> parameters) throws Exception {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        Producer producer = http.createProducer(context, "http://localhost:" + server.getAddress().getPort(), "GET", "api",
                "items", null, null, null, new RestConfiguration(), parameters);
        producer.start();
        return producer;
    }

    private String send(Producer producer) throws Exception {
        authorization.set(null);
        Exchange exchange = new DefaultExchange(context);
        producer.process(exchange);
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(200);
        return authorization.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
