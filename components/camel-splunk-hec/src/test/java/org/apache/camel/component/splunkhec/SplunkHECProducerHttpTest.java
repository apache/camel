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
package org.apache.camel.component.splunkhec;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer must start without sslContextParameters, and send the events over plain HTTP with https=false.
 */
public class SplunkHECProducerHttpTest extends CamelTestSupport {

    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger arrived = new AtomicInteger();
    private HttpServer server;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", httpExchange -> {
            arrived.incrementAndGet();
            String body = new String(httpExchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            requests.add(httpExchange.getRequestMethod() + " " + httpExchange.getRequestURI().getPath() + " "
                         + httpExchange.getRequestHeaders().getFirst("Authorization") + " " + body);
            byte[] answer = "{\"text\":\"Success\",\"code\":0}".getBytes(StandardCharsets.UTF_8);
            httpExchange.sendResponseHeaders(200, answer.length);
            httpExchange.getResponseBody().write(answer);
            httpExchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void testSendOverHttp() {
        sendOverHttp("");
    }

    @Test
    void testSendOverHttpWithSslContextParameters() {
        context.getRegistry().bind("ssl", new SSLContextParameters());
        sendOverHttp("&sslContextParameters=#ssl");
    }

    private void sendOverHttp(String options) {
        String uri = "splunk-hec:localhost:" + server.getAddress().getPort() + "?token=" + TOKEN + "&https=false&bodyOnly=true"
                     + options;

        Exchange exchange = template.send(uri, e -> e.getIn().setBody("hello"));

        assertNull(exchange.getException());
        assertEquals(1, requests.size());
        assertTrue(requests.get(0).startsWith("POST /services/collector/event "), requests.get(0));
        assertTrue(requests.get(0).contains("Splunk " + TOKEN), requests.get(0));
        assertTrue(requests.get(0).contains("\"event\":\"hello\""), requests.get(0));
    }

    @Test
    void testStreamBody() throws Exception {
        template.sendBody(uri("&bodyOnly=true"), new ByteArrayInputStream("hello stream".getBytes(StandardCharsets.UTF_8)));

        assertEquals("hello stream", event().asText());
    }

    @Test
    void testStreamBodyWithHeaders() throws Exception {
        template.sendBodyAndHeader(uri(""), new ByteArrayInputStream("hello stream".getBytes(StandardCharsets.UTF_8)),
                "foo", "bar");

        JsonNode event = event();
        assertEquals("hello stream", event.get("body").asText());
        assertEquals("bar", event.get("headers").get("foo").asText());
    }

    @Test
    void testBytesBody() throws Exception {
        template.sendBody(uri("&bodyOnly=true"), "hello bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello bytes", event().asText());
    }

    @Test
    void testFileBody() throws Exception {
        Path file = Files.writeString(tempDir.resolve("event.txt"), "hello file");

        template.sendBody(uri("&bodyOnly=true"), file.toFile());
        template.sendBody(uri("&bodyOnly=true"), file);

        assertEquals(2, bodies.size());
        assertEquals("hello file", MAPPER.readTree(bodies.get(0)).get("event").asText());
        assertEquals("hello file", MAPPER.readTree(bodies.get(1)).get("event").asText());
    }

    @Test
    void testFileConsumerBody() throws Exception {
        Files.writeString(tempDir.resolve("event.txt"), "hello file consumer");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:file")
                        .pollEnrich("file:" + tempDir.toAbsolutePath() + "?noop=true", 5000)
                        .to(uri("&bodyOnly=true"));
            }
        });

        Exchange exchange = template.send("direct:file", e -> {
        });

        assertNull(exchange.getException());
        assertInstanceOf(GenericFile.class, exchange.getMessage().getBody());
        assertEquals("hello file consumer", event().asText());
    }

    @Test
    void testBodyNotWrittenAsJsonFailsBeforeSending() throws Exception {
        // Jackson cannot write a plain Object: it fails after it has written the long text before it,
        // and this must fail before any request reaches Splunk
        List<Object> body = List.of("x".repeat(20000), new Object());
        Exchange exchange = template.send(uri("&bodyOnly=true"), e -> e.getIn().setBody(body));
        assertNotNull(exchange.getException());

        // the server handles the requests one by one, so once this one is answered the failed one would have arrived
        template.sendBody(uri("&bodyOnly=true"), "hello");
        assertEquals(1, arrived.get());
        assertEquals("hello", event().asText());
    }

    private String uri(String options) {
        return "splunk-hec:localhost:" + server.getAddress().getPort() + "?token=" + TOKEN + "&https=false" + options;
    }

    private JsonNode event() throws Exception {
        assertEquals(1, bodies.size(), bodies::toString);
        return MAPPER.readTree(bodies.get(0)).get("event");
    }

    @Test
    void testStartWithoutSslContextParameters() throws Exception {
        // https=true (the default) without sslContextParameters uses the default SSL context of the JVM
        Producer producer = context.getEndpoint("splunk-hec:localhost:8088?token=" + TOKEN).createProducer();

        assertDoesNotThrow(producer::start);
        producer.stop();
    }
}
