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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The request the producer sends for the bodies and headers it takes, checked against a node that records the requests
 * and answers them as Elasticsearch does.
 */
class ElasticsearchRestClientProducerBodyTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String QUERY = "{\"query\":{\"match\":{\"title\":\"camel\"}}}";

    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private HttpServer node;
    private CamelContext context;
    private ProducerTemplate template;
    private String uri;

    @BeforeEach
    void start() throws Exception {
        node = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        node.createContext("/", this::answer);
        node.start();
        uri = "elasticsearch-rest-client:camel?indexName=my_index&hostAddressesList=127.0.0.1:"
              + node.getAddress().getPort();
        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() {
        context.stop();
        node.stop(0);
    }

    @Test
    void searchSendsAJsonBodyAsTheQuery() throws Exception {
        assertEquals("[{\"title\":\"camel\"}]", template.requestBody(uri + "&operation=SEARCH", QUERY, String.class));

        assertEquals("/my_index/_search", single().path());
        assertEquals(json(QUERY), json(single().body()));
    }

    @Test
    void searchSendsAJsonStreamBodyAsTheQuery() throws Exception {
        template.requestBody(uri + "&operation=SEARCH", new ByteArrayInputStream(QUERY.getBytes(StandardCharsets.UTF_8)),
                String.class);

        assertEquals(json(QUERY), json(single().body()));
    }

    @Test
    void searchPrefersTheQueryHeaderToTheBody() throws Exception {
        String header = "{\"query\":{\"match_all\":{}}}";

        template.requestBodyAndHeader(uri + "&operation=SEARCH", QUERY, ElasticSearchRestClientConstant.SEARCH_QUERY, header,
                String.class);

        assertEquals(json(header), json(single().body()));
    }

    @Test
    void searchStillMatchesTheFieldsOfAMapBody() throws Exception {
        template.requestBody(uri + "&operation=SEARCH", Map.of("title", "camel"), String.class);

        assertEquals(json("{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"camel\"}}]}}}"), json(single().body()));
    }

    @Test
    void searchWithoutBodyStillMatchesEveryDocument() {
        template.requestBody(uri + "&operation=SEARCH", null, String.class);

        assertEquals("", single().body());
    }

    @Test
    void getByIdPrefersTheIdHeaderToALeftoverBody() {
        String document = template.requestBodyAndHeader(uri + "&operation=GET_BY_ID", "leftover",
                ElasticSearchRestClientConstant.ID, "1", String.class);

        assertEquals("{\"title\":\"camel\"}", document);
        assertEquals("GET", single().method());
        assertEquals("/my_index/_doc/1", single().path());
    }

    @Test
    void getByIdStillTakesTheIdOfTheBodyWithoutHeader() {
        template.requestBody(uri + "&operation=GET_BY_ID", "1", String.class);

        assertEquals("/my_index/_doc/1", single().path());
    }

    @Test
    void getByIdOfAMissingDocumentFailsWithTheErrorOfTheServer() {
        Exchange exchange = template.request(uri + "&operation=GET_BY_ID",
                e -> e.getMessage().setHeader(ElasticSearchRestClientConstant.ID, "missing"));

        ResponseException e = assertInstanceOf(ResponseException.class, exchange.getException());
        assertEquals(404, e.getResponse().getStatusLine().getStatusCode());
    }

    @Test
    void deletePrefersTheIdHeaderToALeftoverBody() {
        Object deleted = template.requestBodyAndHeader(uri + "&operation=DELETE", "leftover",
                ElasticSearchRestClientConstant.ID, "1");

        assertEquals(Boolean.TRUE, deleted);
        assertEquals("DELETE", single().method());
        assertEquals("/my_index/_doc/1", single().path());
    }

    private Recorded single() {
        assertEquals(1, requests.size(), "requests sent: " + requests);
        return requests.get(0);
    }

    private static JsonNode json(String text) throws IOException {
        return MAPPER.readTree(text);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        requests.add(new Recorded(method, path, body));
        int status = 200;
        String response;
        if (path.endsWith("/_search")) {
            response = "{\"hits\":{\"hits\":[{\"_source\":{\"title\":\"camel\"}}]}}";
        } else if (path.endsWith("/_doc/missing")) {
            status = 404;
            response = "{\"_index\":\"my_index\",\"_id\":\"missing\",\"found\":false}";
        } else if ("DELETE".equals(method)) {
            response = "{\"_index\":\"my_index\",\"_id\":\"1\",\"result\":\"deleted\"}";
        } else {
            response = "{\"_index\":\"my_index\",\"_id\":\"1\",\"found\":true,\"_source\":{\"title\":\"camel\"}}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record Recorded(String method, String path, String body) {
    }
}
