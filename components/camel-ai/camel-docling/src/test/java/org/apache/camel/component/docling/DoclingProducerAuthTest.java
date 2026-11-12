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
package org.apache.camel.component.docling;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ai.docling.serve.api.health.HealthCheckResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts which credentials the producer puts on the wire for each {@link AuthenticationScheme}, using a stub
 * docling-serve instead of a real one.
 */
class DoclingProducerAuthTest extends CamelTestSupport {

    private final List<Headers> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startStubServer() throws IOException {
        requests.clear();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestHeaders());
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void bearerTokenIsSentInTheAuthorizationHeader() throws Exception {
        assertThat(healthStatus("BEARER", "secret-token")).isEqualTo("ok");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getFirst("Authorization")).isEqualTo("Bearer secret-token");
        assertThat(requests.get(0).getFirst("X-Api-Key")).isNull();
    }

    @Test
    void apiKeyIsSentInTheApiKeyHeader() throws Exception {
        assertThat(healthStatus("API_KEY", "secret-token")).isEqualTo("ok");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getFirst("X-Api-Key")).isEqualTo("secret-token");
        assertThat(requests.get(0).getFirst("Authorization")).isNull();
    }

    @Test
    void noCredentialsAreSentWhenAuthenticationIsDisabled() throws Exception {
        assertThat(healthStatus("NONE", null)).isEqualTo("ok");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getFirst("Authorization")).isNull();
        assertThat(requests.get(0).getFirst("X-Api-Key")).isNull();
    }

    private String healthStatus(String authenticationScheme, String authenticationToken) throws Exception {
        String uri = "docling:CONVERT_TO_MARKDOWN?useDoclingServe=true&doclingServeUrl=http://localhost:"
                     + server.getAddress().getPort()
                     + "&authenticationScheme=" + authenticationScheme
                     + (authenticationToken != null ? "&authenticationToken=" + authenticationToken : "");
        DoclingEndpoint endpoint = context.getEndpoint(uri, DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();
        producer.start();
        try {
            HealthCheckResponse health = producer.getDoclingServeApi().health();
            return health.getStatus();
        } finally {
            producer.stop();
        }
    }
}
