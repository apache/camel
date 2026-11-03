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
package org.apache.camel.component.azure.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.functions.client.FunctionsClientFactory;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient.FunctionInvocationResponse;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * invokeFunction against a JDK HttpServer that stands in for the function app (CAMEL-26247): the request body, the
 * Content-Type of the request and the HTTP status of the response.
 */
class FunctionsProducerInvokeTest extends CamelTestSupport {

    private static final String FUNCTION_APP_URL = "https://myapp.azurewebsites.net";
    private static final byte[] BINARY = { 0x7b, (byte) 0xff, 0x00, (byte) 0xfe, 0x7d };
    private static final String ERROR = "{\"error\":\"boom\"}";

    private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
    private HttpServer server;
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"result\":\"ok\"}";

    @TempDir
    Path tempDir;

    @BeforeEach
    void startFunctionApp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopFunctionApp() {
        server.stop(0);
    }

    @Test
    void textIsSentAsUtf8() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody("Grüße from Camel"));

        assertNull(result.getException());
        RecordedRequest request = nextRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/myfunction", request.path());
        assertArrayEquals("Grüße from Camel".getBytes(StandardCharsets.UTF_8), request.body());
        assertEquals("{\"result\":\"ok\"}", result.getMessage().getBody(String.class));
        assertEquals(200, result.getMessage().getHeader(FunctionsConstants.STATUS_CODE));
    }

    @Test
    void bytesAreSentAsTheyAre() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(BINARY));

        assertNull(result.getException());
        assertArrayEquals(BINARY, nextRequest().body());
    }

    @Test
    void streamIsSentAsItsContent() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(new ByteArrayInputStream(BINARY)));

        assertNull(result.getException());
        assertArrayEquals(BINARY, nextRequest().body());
    }

    @Test
    void fileIsSentAsItsContent() throws Exception {
        Path file = Files.write(tempDir.resolve("payload.bin"), BINARY);

        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(file.toFile()));

        assertNull(result.getException());
        assertArrayEquals(BINARY, nextRequest().body());
    }

    @Test
    void fileOfFileConsumerIsSentAsItsContent() throws Exception {
        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Files.write(inbox.resolve("payload.bin"), BINARY);
        FunctionsEndpoint endpoint = functionsEndpoint("");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + inbox + "?noop=true&initialDelay=0").to(endpoint);
            }
        });

        assertArrayEquals(BINARY, nextRequest().body());
    }

    @ParameterizedTest
    @MethodSource("valuesSentAsText")
    void valueIsSentAsText(Object body, String text) throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(body));

        assertNull(result.getException());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), nextRequest().body());
    }

    static Stream<Arguments> valuesSentAsText() {
        return Stream.of(
                Arguments.of(42, "42"),
                Arguments.of(4.5d, "4.5"),
                Arguments.of(new BigDecimal("12.50"), "12.50"),
                Arguments.of(true, "true"),
                Arguments.of(new StringBuilder("Grüße from Camel"), "Grüße from Camel"));
    }

    @Test
    void noBodySendsAnEmptyRequest() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(null));

        assertNull(result.getException());
        RecordedRequest request = nextRequest();
        assertEquals(0, request.body().length);
        assertNull(request.contentTypes());
    }

    @Test
    void mapIsNotSentAsItsToString() {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody(Map.of("name", "Camel")));

        assertInstanceOf(InvalidPayloadException.class, result.getException());
        assertTrue(requests.isEmpty(), "nothing must be sent to the function");
    }

    @Test
    void getSendsNoBody() throws Exception {
        Exchange result = template.send(functionsEndpoint("&httpMethod=GET"),
                e -> e.getIn().setBody(Map.of("name", "Camel")));

        assertNull(result.getException());
        RecordedRequest request = nextRequest();
        assertEquals("GET", request.method());
        assertEquals(0, request.body().length);
        assertNull(request.contentTypes());
    }

    @Test
    void defaultContentTypeWithoutHttpHeaders() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody("{\"name\":\"Camel\"}"));

        assertNull(result.getException());
        assertEquals(List.of("application/json"), nextRequest().contentTypes());
    }

    @Test
    void defaultContentTypeWithHttpHeadersWithoutContentType() throws Exception {
        Exchange result = template.send(functionsEndpoint(""), e -> {
            e.getIn().setBody("{\"name\":\"Camel\"}");
            e.getIn().setHeader(FunctionsConstants.HTTP_HEADERS, Map.of("X-Custom", "value"));
        });

        assertNull(result.getException());
        assertEquals(List.of("application/json"), nextRequest().contentTypes());
    }

    @Test
    void contentTypeOfHttpHeadersIsKept() throws Exception {
        // HTTP header names are case-insensitive: the default must not be added next to content-type
        Exchange result = template.send(functionsEndpoint(""), e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(FunctionsConstants.HTTP_HEADERS, Map.of("content-type", "text/plain"));
        });

        assertNull(result.getException());
        assertEquals(List.of("text/plain"), nextRequest().contentTypes());
    }

    @Test
    void serverErrorFailsTheExchange() throws Exception {
        responseStatus = 500;
        responseBody = ERROR;

        Exchange result = template.send(functionsEndpoint(""), e -> e.getIn().setBody("Hello"));

        HttpOperationFailedException cause = assertInstanceOf(HttpOperationFailedException.class, result.getException());
        assertEquals(500, cause.getStatusCode());
        assertEquals(ERROR, cause.getResponseBody());
        assertEquals("details", cause.getResponseHeaders().get("X-Function-Error"));
        assertEquals(FUNCTION_APP_URL + "/api/myfunction", cause.getUri());
        assertEquals(500, result.getMessage().getHeader(FunctionsConstants.STATUS_CODE));
        // as in camel-http, the message body is still the request
        assertEquals("Hello", result.getMessage().getBody(String.class));
        assertNotNull(nextRequest());
    }

    @Test
    void clientErrorFailsTheExchange() {
        responseStatus = 404;
        responseBody = ERROR;

        Exchange result = template.send(functionsEndpoint("&httpMethod=GET"), e -> e.getIn().setBody(null));

        HttpOperationFailedException cause = assertInstanceOf(HttpOperationFailedException.class, result.getException());
        assertEquals(404, cause.getStatusCode());
        assertEquals(ERROR, cause.getResponseBody());
    }

    @Test
    void errorStatusIsTheResponseWithThrowExceptionOnFailureFalse() {
        responseStatus = 500;
        responseBody = ERROR;

        Exchange result = template.send(functionsEndpoint("&throwExceptionOnFailure=false"),
                e -> e.getIn().setBody("Hello"));

        assertNull(result.getException());
        assertEquals(ERROR, result.getMessage().getBody(String.class));
        assertEquals(500, result.getMessage().getHeader(FunctionsConstants.STATUS_CODE));
    }

    /**
     * The azure-functions endpoint of the test, with an invocation client that sends to the fake function app.
     */
    private FunctionsEndpoint functionsEndpoint(String options) {
        FunctionsEndpoint endpoint = context.getEndpoint(
                "azure-functions:myapp/myfunction?functionKey=secret" + options, FunctionsEndpoint.class);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        endpoint.setClientFactory(new FunctionsClientFactory() {
            @Override
            public FunctionsInvocationClient createInvocationClient(FunctionsConfiguration configuration) {
                return new FunctionsInvocationClient(
                        configuration.getFunctionKey(), configuration.getHostKey(),
                        configuration.getConnectionTimeout(), configuration.getReadTimeout()) {
                    @Override
                    public FunctionInvocationResponse invoke(
                            String url, String method, Object body, Map<String, String> headers)
                            throws IOException, InterruptedException {
                        return super.invoke(url.replace(FUNCTION_APP_URL, baseUrl), method, body, headers);
                    }
                };
            }
        });
        return endpoint;
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        RecordedRequest request = requests.poll(10, TimeUnit.SECONDS);
        assertNotNull(request, "the function got no request");
        return request;
    }

    private void handle(HttpExchange http) throws IOException {
        byte[] body;
        try (InputStream in = http.getRequestBody()) {
            body = in.readAllBytes();
        }
        requests.add(new RecordedRequest(
                http.getRequestMethod(), http.getRequestURI().getPath(), http.getRequestHeaders().get("Content-Type"), body));

        byte[] answer = responseBody.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("X-Function-Error", "details");
        http.sendResponseHeaders(responseStatus, answer.length);
        try (OutputStream out = http.getResponseBody()) {
            out.write(answer);
        }
    }

    private record RecordedRequest(String method, String path, List<String> contentTypes, byte[] body) {
    }
}
