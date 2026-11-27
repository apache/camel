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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * With enableSniffer, a failed request makes the sniffer sniff the nodes again, and once more sniffAfterFailureDelay
 * later, instead of only every snifferInterval.
 */
class ElasticsearchProducerSnifferTest {

    private final AtomicInteger sniffs = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        // the node fails every request with 503, which the client counts as a failure of the node, and answers the
        // sniffing of the nodes with no node
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aFailedRequestMakesTheSnifferSniffAgain() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context.getEndpoint("elasticsearch:test-cluster?operation=Ping&hostAddresses=127.0.0.1:"
                                                    + server.getAddress().getPort()
                                                    + "&enableSniffer=true&snifferInterval=3600000&sniffAfterFailureDelay=100");
            ElasticsearchProducer producer = (ElasticsearchProducer) endpoint.createProducer();
            producer.start();
            try {
                // the sniffer ignores failures until its first round is done, so the request is repeated until a
                // failure is seen; the first round is the only one in the next hour without that
                await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() -> {
                    Exchange exchange = endpoint.createExchange();
                    producer.process(exchange);
                    return sniffs.get() >= 2;
                });
                // and the round sniffAfterFailureDelay after the one of the failure
                await().atMost(20, TimeUnit.SECONDS).until(() -> sniffs.get() >= 3);
            } finally {
                producer.stop();
            }
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        if (!exchange.getRequestURI().getPath().startsWith("/_nodes")) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        sniffs.incrementAndGet();
        byte[] bytes = "{\"nodes\":{}}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
