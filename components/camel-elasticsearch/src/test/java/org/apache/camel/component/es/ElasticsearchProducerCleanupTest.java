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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer removes the headers it set from the endpoint options, and closes the client it created with
 * disconnect=true, when the request succeeds, when it fails, and when a scroll is read after the exchange.
 */
class ElasticsearchProducerCleanupTest {

    private static final String SHARDS = "\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0}";
    private static final String QUERY = "{\"query\":{\"match_all\":{}}}";
    private static final List<String> OPTION_HEADERS = List.of(
            ElasticsearchConstants.PARAM_INDEX_NAME, ElasticsearchConstants.PARAM_SIZE, ElasticsearchConstants.PARAM_FROM,
            ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, ElasticsearchConstants.PARAM_DOCUMENT_MODE);

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger scrolls = new AtomicInteger();
    private final List<RestClient> createdClients = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private CamelContext context;
    private String uri;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        uri = "elasticsearch:test-cluster?hostAddresses=127.0.0.1:" + server.getAddress().getPort();
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stop() {
        context.stop();
        server.stop(0);
    }

    @Test
    void optionHeadersAreRemovedAfterASuccessfulRequest() throws Exception {
        Exchange exchange = send(uri + "&operation=Ping&enableDocumentOnlyMode=true", null);

        assertNull(exchange.getException());
        assertEquals(Boolean.TRUE, exchange.getMessage().getBody());
        assertNoOptionHeaders(exchange);
    }

    @Test
    void aDocumentOnlyModeHeaderOfTheMessageIsKept() throws Exception {
        ElasticsearchProducer producer = startProducer(uri + "&operation=Ping&enableDocumentOnlyMode=true");
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setHeader(ElasticsearchConstants.PARAM_DOCUMENT_MODE, false);

        producer.process(exchange);

        assertNull(exchange.getException());
        assertEquals(Boolean.FALSE, exchange.getMessage().getHeader(ElasticsearchConstants.PARAM_DOCUMENT_MODE));
        producer.stop();
    }

    @Test
    void optionHeadersAreRemovedAndTheClientClosedWhenTheRequestFails() throws Exception {
        Exchange exchange = send(uri + "&operation=Index&indexName=failing&size=5&from=1&disconnect=true",
                Map.of("name", "camel"));

        assertNotNull(exchange.getException(), "the node rejected the request");
        assertTrue(requests.stream().anyMatch(r -> r.endsWith(" /failing/_doc")), "requests sent: " + requests);
        assertNoOptionHeaders(exchange);
        assertEquals(1, createdClients.size());
        assertFalse(createdClients.get(0).isRunning(), "the client the failed exchange used was not closed");
    }

    @Test
    void optionHeadersAreRemovedAndTheClientClosedWhenTheBodyIsRejected() throws Exception {
        // the producer throws before it sends the request: the Index operation needs a body
        Exchange exchange = send(uri + "&operation=Index&indexName=idx&size=5&from=1&disconnect=true", null);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(requests.isEmpty(), "requests sent: " + requests);
        assertNoOptionHeaders(exchange);
        assertEquals(1, createdClients.size());
        assertFalse(createdClients.get(0).isRunning(), "the client the failed exchange used was not closed");
    }

    @Test
    void aScrollWithDisconnectIsReadToTheEndAndClosesTheClientOnClose() throws Exception {
        ElasticsearchProducer producer
                = startProducer(uri + "&operation=Search&indexName=idx&useScroll=true&disconnect=true");
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setBody(QUERY);

        producer.process(exchange);

        assertNull(exchange.getException());
        ElasticsearchScrollRequestIterator<?> iterator
                = assertInstanceOf(ElasticsearchScrollRequestIterator.class, exchange.getMessage().getBody());
        List<String> ids = new ArrayList<>();
        try (iterator) {
            while (iterator.hasNext()) {
                Hit<?> hit = iterator.next();
                ids.add(hit.id());
            }
            assertTrue(createdClients.get(0).isRunning(), "the client was closed before the scroll was closed");
        }

        assertEquals(List.of("1", "2"), ids);
        assertTrue(requests.contains("DELETE /_search/scroll"), "requests sent: " + requests);
        assertEquals(1, createdClients.size());
        assertFalse(createdClients.get(0).isRunning(), "the client of the scroll was not closed with the scroll");
        // the next exchange creates a new client, the scroll kept the one it read with
        assertNull(producer.getClient());
        assertNoOptionHeaders(exchange);
        producer.stop();
    }

    private Exchange send(String endpointUri, Object body) throws Exception {
        ElasticsearchProducer producer = startProducer(endpointUri);
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setBody(body);
        producer.process(exchange);
        producer.stop();
        return exchange;
    }

    private ElasticsearchProducer startProducer(String endpointUri) {
        ElasticsearchEndpoint endpoint = context.getEndpoint(endpointUri, ElasticsearchEndpoint.class);
        ElasticsearchProducer producer = new ElasticsearchProducer(endpoint, endpoint.getConfiguration()) {
            @Override
            RestClient createClient() {
                RestClient client = super.createClient();
                createdClients.add(client);
                return client;
            }
        };
        producer.start();
        return producer;
    }

    private static void assertNoOptionHeaders(Exchange exchange) {
        for (String header : OPTION_HEADERS) {
            assertFalse(exchange.getMessage().getHeaders().containsKey(header),
                    header + " set from the options is still on the message");
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        requests.add(method + " " + path);
        int status = 200;
        String response;
        if ("HEAD".equals(method)) {
            response = null;
        } else if (path.startsWith("/failing/")) {
            status = 400;
            response = "{\"error\":{\"type\":\"mapper_parsing_exception\",\"reason\":\"rejected\"},\"status\":400}";
        } else if (path.endsWith("/_search")) {
            response = hits("\"_scroll_id\":\"s1\",", "1");
        } else if ("/_search/scroll".equals(path) && "DELETE".equals(method)) {
            response = "{\"succeeded\":true,\"num_freed\":1}";
        } else if ("/_search/scroll".equals(path)) {
            response = scrolls.incrementAndGet() == 1 ? hits("\"_scroll_id\":\"s1\",", "2") : hits("", null);
        } else {
            response = "{}";
        }
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (response == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String hits(String scrollId, String id) {
        String hit = id == null ? "" : "{\"_index\":\"idx\",\"_id\":\"" + id + "\",\"_source\":{\"n\":" + id + "}}";
        return "{" + scrollId + "\"took\":1,\"timed_out\":false," + SHARDS
               + ",\"hits\":{\"total\":{\"value\":2,\"relation\":\"eq\"},\"hits\":[" + hit + "]}}";
    }
}
