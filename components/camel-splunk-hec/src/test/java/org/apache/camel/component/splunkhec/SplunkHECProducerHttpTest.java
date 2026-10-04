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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer must start without sslContextParameters, and send the events over plain HTTP with https=false.
 */
public class SplunkHECProducerHttpTest extends CamelTestSupport {

    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", httpExchange -> {
            String body = new String(httpExchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
    void testStartWithoutSslContextParameters() throws Exception {
        // https=true (the default) without sslContextParameters uses the default SSL context of the JVM
        Producer producer = context.getEndpoint("splunk-hec:localhost:8088?token=" + TOKEN).createProducer();

        assertDoesNotThrow(producer::start);
        producer.stop();
    }
}
