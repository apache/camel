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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request the producer sends for the bodies it takes, checked against a node that records the requests and answers
 * them as Elasticsearch does.
 */
class ElasticsearchProducerBodyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SHARDS = "\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0}";
    private static final String WRITTEN = "{\"_index\":\"idx\",\"_id\":\"1\",\"_version\":1,\"result\":\"%s\"," + SHARDS
                                          + ",\"_seq_no\":0,\"_primary_term\":1}";
    private static final String FOUND
            = "{\"_index\":\"idx\",\"_id\":\"1\",\"_version\":1,\"_seq_no\":0,\"_primary_term\":1,\"found\":true,"
              + "\"_source\":{\"name\":\"camel\"}}";
    private static final String HITS = "{\"took\":1,\"timed_out\":false," + SHARDS
                                       + ",\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}";

    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private CamelContext context;
    private ProducerTemplate template;
    private String uri;

    @TempDir
    Path dir;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        uri = "elasticsearch:test-cluster?hostAddresses=127.0.0.1:" + server.getAddress().getPort();
        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() {
        context.stop();
        server.stop(0);
    }

    @Test
    void aBuiltIndexRequestIsSentAsItIs() throws Exception {
        IndexRequest<Object> request = IndexRequest.of(b -> b.index("idx").id("1").document(Map.of("name", "camel")));

        assertEquals("1", template.requestBody(uri + "&indexName=other", request));

        Recorded sent = single();
        assertEquals("PUT", sent.method());
        assertEquals("/idx/_doc/1", sent.path());
        assertEquals(json("{\"name\":\"camel\"}"), json(sent.body()));
    }

    @Test
    void aBuiltUpdateRequestIsSentAsItIs() throws Exception {
        UpdateRequest<Object, Object> request
                = UpdateRequest.of(b -> b.index("idx").id("1").doc(Map.of("name", "camel")));

        assertEquals("1", template.requestBody(uri + "&indexName=other", request));

        Recorded sent = single();
        assertEquals("/idx/_update/1", sent.path());
        assertEquals(json("{\"doc\":{\"name\":\"camel\"}}"), json(sent.body()));
    }

    @Test
    void aBuiltGetRequestIsSentAsItIs() {
        GetResponse<?> response
                = template.requestBody(uri, GetRequest.of(b -> b.index("idx").id("1")), GetResponse.class);

        assertTrue(response.found());
        assertEquals("GET", single().method());
        assertEquals("/idx/_doc/1", single().path());
    }

    @Test
    void aBuiltDeleteRequestIsSentAsItIs() {
        Object result = template.requestBody(uri, DeleteRequest.of(b -> b.index("idx").id("1")));

        assertEquals(Result.Deleted, result);
        assertEquals("DELETE", single().method());
        assertEquals("/idx/_doc/1", single().path());
    }

    @Test
    void aBuiltSearchRequestIsSentAsItIs() throws Exception {
        SearchRequest request = SearchRequest.of(b -> b.index("idx").size(3).query(q -> q.matchAll(m -> m)));

        assertInstanceOf(HitsMetadata.class, template.requestBody(uri + "&indexName=other", request));

        Recorded sent = single();
        assertEquals("/idx/_search", sent.path());
        assertEquals(json("{\"query\":{\"match_all\":{}},\"size\":3}"), json(sent.body()));
    }

    @Test
    void aBuiltMultiSearchRequestIsSentAsItIs() {
        MsearchRequest request = MsearchRequest.of(b -> b.index("idx")
                .searches(s -> s.header(h -> h).body(q -> q.query(m -> m.matchAll(a -> a)))));

        List<?> responses = template.requestBody(uri, request, List.class);

        assertEquals(1, responses.size());
        assertEquals("/idx/_msearch", single().path());
        assertTrue(single().body().contains("match_all"), single().body());
    }

    @Test
    void aBuiltMultiGetRequestIsSentAsItIs() throws Exception {
        List<?> docs = template.requestBody(uri, MgetRequest.of(b -> b.index("idx").ids("1")), List.class);

        assertEquals(1, docs.size());
        assertEquals("/idx/_mget", single().path());
        assertEquals(json("{\"ids\":[\"1\"]}"), json(single().body()));
    }

    @Test
    void aBuiltBulkRequestIsSentAsItIs() throws Exception {
        BulkRequest request = BulkRequest.of(b -> b.operations(
                o -> o.index(i -> i.index("idx").id("1").document(Map.of("name", "camel")))));

        List<?> items = template.requestBody(uri, request, List.class);

        assertEquals(1, items.size());
        assertEquals("/_bulk", single().path());
        String[] lines = single().body().split("\n");
        assertEquals(json("{\"index\":{\"_id\":\"1\",\"_index\":\"idx\"}}"), json(lines[0]));
        assertEquals(json("{\"name\":\"camel\"}"), json(lines[1]));
    }

    @Test
    void aBuiltDeleteIndexRequestIsSentAsItIs() {
        Object acknowledged = template.requestBody(uri, DeleteIndexRequest.of(b -> b.index("idx")));

        assertEquals(Boolean.TRUE, acknowledged);
        assertEquals("DELETE", single().method());
        assertEquals("/idx", single().path());
    }

    @Test
    void deleteIndexDeletesTheIndexNamedByTheBodyWithoutHeaderOrOption() {
        Object acknowledged = template.requestBody(uri + "&operation=DeleteIndex", "idx");

        assertEquals(Boolean.TRUE, acknowledged);
        assertEquals("/idx", single().path());
    }

    @Test
    void deleteIndexPrefersTheIndexNameOption() {
        Object acknowledged = template.requestBody(uri + "&operation=DeleteIndex&indexName=idx", "other");

        assertEquals(Boolean.TRUE, acknowledged);
        assertEquals("/idx", single().path());
    }

    @Test
    void indexFailsClearlyWithoutBody() {
        Exchange exchange = template.request(uri + "&operation=Index&indexName=idx", e -> e.getMessage().setBody(null));

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("The body of the Index operation cannot be null: it is the document to index, or its request",
                e.getMessage());
        assertTrue(requests.isEmpty(), "a request was sent");
    }

    @Test
    void updateFailsClearlyWithoutBody() {
        Exchange exchange = template.request(uri + "&operation=Update&indexName=idx", e -> {
            e.getMessage().setHeader(ElasticsearchConstants.PARAM_INDEX_ID, "1");
            e.getMessage().setBody(null);
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("The body of the Update operation cannot be null: it is the update of the document, or its request",
                e.getMessage());
        assertTrue(requests.isEmpty(), "a request was sent");
    }

    @Test
    void indexReadsAFileAsTheJsonOfTheDocument() throws Exception {
        File file = write("doc.json", "{\"name\":\"camel\"}").toFile();

        assertEquals("1", template.requestBody(uri + "&operation=Index&indexName=idx", file));

        assertEquals(json("{\"name\":\"camel\"}"), json(single().body()));
    }

    @Test
    void indexReadsAPathAsTheJsonOfTheDocument() throws Exception {
        Path path = write("doc.json", "{\"name\":\"camel\"}");

        assertEquals("1", template.requestBody(uri + "&operation=Index&indexName=idx", path));

        assertEquals(json("{\"name\":\"camel\"}"), json(single().body()));
    }

    @Test
    void indexReadsTheFileOfTheFileComponentAsTheJsonOfTheDocument() throws Exception {
        File file = write("doc.json", "{\"name\":\"camel\"}").toFile();
        Object genericFile = FileConsumer.asGenericFile(dir.toString(), file, null, false);

        assertEquals("1", template.requestBody(uri + "&operation=Index&indexName=idx", genericFile));

        assertEquals(json("{\"name\":\"camel\"}"), json(single().body()));
    }

    @Test
    void updateReadsAFileAsJson() throws Exception {
        File file = write("update.json", "{\"doc\":{\"name\":\"camel\"}}").toFile();

        assertEquals("1", template.requestBodyAndHeader(uri + "&operation=Update&indexName=idx", file,
                ElasticsearchConstants.PARAM_INDEX_ID, "1"));

        assertEquals("/idx/_update/1", single().path());
        assertEquals(json("{\"doc\":{\"name\":\"camel\"}}"), json(single().body()));
    }

    @Test
    void getByIdStillTakesTheIdAsText() {
        GetResponse<?> response = template.requestBody(uri + "&operation=GetById&indexName=idx", "1", GetResponse.class);

        assertTrue(response.found());
        assertEquals("/idx/_doc/1", single().path());
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
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
        byte[] response = response(method, path).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static String response(String method, String path) {
        if (path.endsWith("/_search")) {
            return HITS;
        } else if (path.endsWith("/_msearch")) {
            return "{\"took\":1,\"responses\":[" + HITS.replaceFirst("\\}$", ",\"status\":200}") + "]}";
        } else if (path.endsWith("/_mget")) {
            return "{\"docs\":[" + FOUND + "]}";
        } else if (path.endsWith("/_bulk")) {
            return "{\"took\":1,\"errors\":false,\"items\":[{\"index\":"
                   + String.format(WRITTEN, "created").replaceFirst("\\}$", ",\"status\":201}") + "}]}";
        } else if (path.contains("/_update/")) {
            return String.format(WRITTEN, "updated");
        } else if (path.contains("/_doc")) {
            return switch (method) {
                case "GET" -> FOUND;
                case "DELETE" -> String.format(WRITTEN, "deleted");
                default -> String.format(WRITTEN, "created");
            };
        }
        // the deletion of an index
        return "{\"acknowledged\":true}";
    }

    private record Recorded(String method, String path, String body) {
    }
}
