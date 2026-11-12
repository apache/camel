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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path of the request the producer sends: the document id and the index name are each one path segment, checked
 * against a node that records the raw request and answers it as Elasticsearch does.
 */
class ElasticsearchRestClientRequestPathTest {

    private static final String DOCUMENT_ID = "a/b?c#d e%";
    private static final String ENCODED_DOCUMENT_ID = "a%2Fb%3Fc%23d%20e%25";

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
        uri = "elasticsearch-rest-client:camel?hostAddressesList=127.0.0.1:" + node.getAddress().getPort();
        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() {
        context.stop();
        node.stop(0);
    }

    static Stream<Arguments> documentIds() {
        return Stream.of(
                Arguments.of("a/b", "a%2Fb"),
                Arguments.of("1?x=y", "1%3Fx%3Dy"),
                Arguments.of("1#2", "1%232"),
                Arguments.of("a b", "a%20b"),
                Arguments.of("100%", "100%25"),
                Arguments.of("café", "caf%C3%A9"),
                Arguments.of(DOCUMENT_ID, ENCODED_DOCUMENT_ID));
    }

    @ParameterizedTest
    @MethodSource("documentIds")
    void getByIdSendsTheIdAsOnePathSegment(String id, String encoded) {
        String document = template.requestBodyAndHeader(uri + "&operation=GET_BY_ID&indexName=my_index", null,
                ElasticSearchRestClientConstant.ID, id, String.class);

        assertEquals("{\"title\":\"camel\"}", document);
        assertEquals("/my_index/_doc/" + encoded, single().rawPath());
        assertNull(single().rawQuery());
    }

    @Test
    void getByIdSendsTheIdOfTheBodyAsOnePathSegment() {
        template.requestBody(uri + "&operation=GET_BY_ID&indexName=my_index", DOCUMENT_ID, String.class);

        assertEquals("/my_index/_doc/" + ENCODED_DOCUMENT_ID, single().rawPath());
        assertNull(single().rawQuery());
    }

    @Test
    void indexSendsTheIdAsOnePathSegment() {
        String id = template.requestBodyAndHeader(uri + "&operation=INDEX_OR_UPDATE&indexName=my_index",
                "{\"title\":\"camel\"}", ElasticSearchRestClientConstant.ID, DOCUMENT_ID, String.class);

        assertEquals("1", id);
        assertEquals("POST", single().method());
        assertEquals("/my_index/_doc/" + ENCODED_DOCUMENT_ID, single().rawPath());
        assertNull(single().rawQuery());
    }

    @Test
    void indexWithoutIdStillLetsTheServerChooseTheId() {
        template.requestBody(uri + "&operation=INDEX_OR_UPDATE&indexName=my_index", "{\"title\":\"camel\"}", String.class);

        assertEquals("/my_index/_doc", single().rawPath());
    }

    @Test
    void deleteSendsTheIdAsOnePathSegment() {
        Object deleted = template.requestBodyAndHeader(uri + "&operation=DELETE&indexName=my_index", null,
                ElasticSearchRestClientConstant.ID, DOCUMENT_ID);

        assertEquals(Boolean.TRUE, deleted);
        assertEquals("DELETE", single().method());
        assertEquals("/my_index/_doc/" + ENCODED_DOCUMENT_ID, single().rawPath());
        assertNull(single().rawQuery());
    }

    @ParameterizedTest
    @ValueSource(strings = { ".", ".." })
    void dotSegmentDocumentIdIsRejected(String id) {
        Exchange exchange = template.request(uri + "&operation=GET_BY_ID&indexName=my_index",
                e -> e.getMessage().setHeader(ElasticSearchRestClientConstant.ID, id));

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(requests.isEmpty(), "requests sent: " + requests);
    }

    @Test
    void indexNamesAndPatternsOfASearchAreSentAsOnePathSegment() {
        template.requestBodyAndHeader(uri + "&operation=SEARCH", null, ElasticSearchRestClientConstant.INDEX_NAME,
                "logs-*,my_index", String.class);

        assertEquals("/logs-*%2Cmy_index/_search", single().rawPath());
        // the node decodes the segment back to the list of index names and patterns
        assertEquals("/logs-*,my_index/_search", single().path());
    }

    @ParameterizedTest
    @ValueSource(strings = { "my index", "a/b", "a?b", "a#b", "a\\b", "a|b", "a\"b", "<a>", ".", "..", "a,,b", "a," })
    void indexNameWithCharactersElasticsearchDoesNotAllowIsRejected(String indexName) {
        Exchange exchange = template.request(uri + "&operation=GET_BY_ID", e -> {
            e.getMessage().setHeader(ElasticSearchRestClientConstant.INDEX_NAME, indexName);
            e.getMessage().setHeader(ElasticSearchRestClientConstant.ID, "1");
        });

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(requests.isEmpty(), "requests sent: " + requests);
    }

    @Test
    void getByIdResponseWithoutSourceReturnsNoDocument() {
        Exchange exchange = template.request(uri + "&operation=GET_BY_ID&indexName=my_index",
                e -> e.getMessage().setHeader(ElasticSearchRestClientConstant.ID, "nosource"));

        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getBody());
    }

    private Recorded single() {
        assertEquals(1, requests.size(), "requests sent: " + requests);
        return requests.get(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String method = exchange.getRequestMethod();
        String rawPath = exchange.getRequestURI().getRawPath();
        requests.add(new Recorded(
                method, rawPath, exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery()));
        String response;
        if (rawPath.endsWith("/_search")) {
            response = "{\"hits\":{\"hits\":[{\"_source\":{\"title\":\"camel\"}}]}}";
        } else if (rawPath.endsWith("/_doc/nosource")) {
            response = "{\"_index\":\"my_index\",\"_id\":\"nosource\",\"found\":true}";
        } else if ("DELETE".equals(method)) {
            response = "{\"_index\":\"my_index\",\"_id\":\"1\",\"result\":\"deleted\"}";
        } else if ("POST".equals(method)) {
            response = "{\"_index\":\"my_index\",\"_id\":\"1\",\"result\":\"created\"}";
        } else {
            response = "{\"_index\":\"my_index\",\"_id\":\"1\",\"found\":true,\"_source\":{\"title\":\"camel\"}}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record Recorded(String method, String rawPath, String path, String rawQuery) {
    }
}
