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
package org.apache.camel.component.ibm.watsonx.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.gson.JsonParser;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.ColumnsCollection;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.Table;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.TableColumDetail;
import com.ibm.cloud.watsonxdata.watsonx_data.v3.model.TableColumDetailColumnsItems;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The getAllColumns and updateTable operations, with a JDK HttpServer as the watsonx.data service and as the IAM token
 * service.
 */
class WatsonxDataProducerTest extends CamelTestSupport {

    private static final String TABLE_PATH = "/catalogs/iceberg_data/schemas/sales/tables/orders";
    private static final String TABLE_PATCH = "{\"name\":\"orders_v2\"}";
    private static final String ACCEPTED_BODIES
            = "a java.util.Map, or a JSON object as text (a String, byte[], InputStream or file)";

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger patchRequests = new AtomicInteger();
    private volatile String columnsRequestUri;
    private volatile String patchRequestUri;
    private volatile String patchContentType;
    private volatile String patchBody;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // the IAM token service, which the IamAuthenticator of the endpoint asks for a token for the API key
        server.createContext("/identity/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, "{\"access_token\":\"test-access-token\",\"refresh_token\":\"test-refresh-token\","
                              + "\"token_type\":\"Bearer\",\"expires_in\":3600,\"expiration\":"
                              + (Instant.now().getEpochSecond() + 3600) + "}");
        });
        server.createContext("/columns", exchange -> {
            columnsRequestUri = exchange.getRequestURI().toString();
            respond(exchange, "{\"columns\":[{\"catalog\":\"iceberg_data\",\"schema\":\"sales\",\"table\":\"orders\","
                              + "\"columns\":[{\"column\":\"id\",\"type\":\"bigint\",\"nullable\":false,\"index\":1,"
                              + "\"comment\":\"the order id\"}]}],\"count\":1}");
        });
        server.createContext(TABLE_PATH, exchange -> {
            patchRequests.incrementAndGet();
            patchRequestUri = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            patchContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            patchBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, "{\"name\":\"orders_v2\",\"columns\":[{\"name\":\"id\",\"type\":\"bigint\"}]}");
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void useStubTokenService() {
        // the endpoint authenticates with an IAM API key: take the token from the stub instead of IBM Cloud
        WatsonxDataEndpoint endpoint = context.getEndpoint(watsonxDataUri(), WatsonxDataEndpoint.class);
        ((IamAuthenticator) endpoint.getWatsonxDataService().getAuthenticator()).setURL(serverUrl());
    }

    @Test
    void getAllColumnsParsesColumnsWithDynamicProperties() {
        Object body = template.requestBodyAndHeader("direct:start", null, WatsonxDataConstants.OPERATION,
                WatsonxDataOperations.getAllColumns);

        ColumnsCollection columns = assertInstanceOf(ColumnsCollection.class, body);
        assertEquals(1L, columns.getCount());
        TableColumDetail table = columns.getColumns().get(0);
        assertEquals("orders", table.getTable());
        TableColumDetailColumnsItems column = table.getColumns().get(0);
        assertEquals("id", column.getColumn());
        assertEquals("bigint", column.getType());
        assertFalse(column.isNullable());
        // a property the model does not declare is kept as a dynamic property of the column
        assertEquals("the order id", column.get("comment"));

        assertTrue(columnsRequestUri.startsWith("/columns?"), columnsRequestUri);
        assertEquals(Map.of("catalog_name", "iceberg_data", "schema_name", "sales"), queryParameters(columnsRequestUri));
    }

    @Test
    void updateTableSendsMapBody() {
        Table table = updateTable(Map.of("name", "orders_v2"));

        assertEquals("orders_v2", table.getName());
        assertPatchSent();
    }

    static Stream<Named<Object>> jsonTextBodies() {
        return Stream.of(
                Named.of("String", TABLE_PATCH),
                Named.of("byte[]", TABLE_PATCH.getBytes(StandardCharsets.UTF_8)),
                Named.of("InputStream", new ByteArrayInputStream(TABLE_PATCH.getBytes(StandardCharsets.UTF_8))));
    }

    @ParameterizedTest
    @MethodSource("jsonTextBodies")
    void updateTableSendsJsonTextBody(Object body) {
        Table table = updateTable(body);

        assertEquals("orders_v2", table.getName());
        assertPatchSent();
    }

    @Test
    void updateTableSendsJsonFileBody() throws IOException {
        Path file = Files.writeString(dir.resolve("patch.json"), TABLE_PATCH);

        Table table = updateTable(file.toFile());

        assertEquals("orders_v2", table.getName());
        assertPatchSent();
    }

    @Test
    void updateTableWithoutBodyFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> updateTable(null));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Request body with table patch data is required for updateTable operation: " + ACCEPTED_BODIES,
                cause.getMessage());
        assertEquals(0, patchRequests.get());
    }

    static Stream<Named<Object>> bodiesThatAreNotJsonObjects() {
        return Stream.of(
                Named.of("JSON array", "[\"orders_v2\"]"),
                Named.of("text that is not JSON", "name=orders_v2"),
                Named.of("bytes that are not UTF-8", new byte[] { (byte) 0xff, (byte) 0xfe }),
                Named.of("POJO", new TableRename("orders_v2")));
    }

    @ParameterizedTest
    @MethodSource("bodiesThatAreNotJsonObjects")
    void updateTableWithBodyThatIsNotJsonObjectFails(Object body) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> updateTable(body));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The body of the updateTable operation is not a JSON object: it must be " + ACCEPTED_BODIES,
                cause.getMessage());
        assertEquals(0, patchRequests.get());
    }

    private Table updateTable(Object body) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxDataConstants.OPERATION, WatsonxDataOperations.updateTable);
        headers.put(WatsonxDataConstants.TABLE_NAME, "orders");
        return template.requestBodyAndHeaders("direct:start", body, headers, Table.class);
    }

    private void assertPatchSent() {
        assertEquals("PATCH " + TABLE_PATH + "?engine_id=presto-01", patchRequestUri);
        assertTrue(patchContentType.startsWith("application/merge-patch+json"), patchContentType);
        assertEquals(JsonParser.parseString(TABLE_PATCH), JsonParser.parseString(patchBody));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(watsonxDataUri());
            }
        };
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String watsonxDataUri() {
        return "ibm-watsonx-data:test?apiKey=test-api-key&catalogName=iceberg_data&schemaName=sales&engineId=presto-01"
               + "&serviceUrl=" + serverUrl();
    }

    private static Map<String, String> queryParameters(String uri) {
        Map<String, String> answer = new HashMap<>();
        for (String pair : uri.substring(uri.indexOf('?') + 1).split("&")) {
            String[] nameAndValue = pair.split("=", 2);
            answer.put(URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8));
        }
        return answer;
    }

    // a POJO the type converters cannot turn into a Map: its text is not JSON
    record TableRename(String name) {
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
