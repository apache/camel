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
package org.apache.camel.component.cometd;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CometdAllowedTransportsTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port defaultTransportsPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port customTransportsPort = AvailablePortFinder.find();

    @Test
    void testDefaultTransportsRejectsJsonp() throws Exception {
        HttpResponse<String> response = sendJsonpHandshake(defaultTransportsPort.getPort());
        // Default allowedTransports is "websocket,long-polling", so JSONP (callback-polling) is rejected
        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains("\"successful\":true"));
    }

    @Test
    void testDefaultTransportsAllowsLongPolling() throws Exception {
        HttpResponse<String> response = sendLongPollingHandshake(defaultTransportsPort.getPort());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"successful\":true"));
        assertFalse(response.body().contains("callback-polling"));
    }

    @Test
    void testCustomTransportsAllowsJsonpWhenConfigured() throws Exception {
        HttpResponse<String> response = sendJsonpHandshake(customTransportsPort.getPort());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("cb(["));
        assertTrue(response.body().contains("\"successful\":true"));
    }

    private HttpResponse<String> sendJsonpHandshake(int port) throws Exception {
        String msg
                = "[{\"channel\":\"/meta/handshake\",\"version\":\"1.0\",\"supportedConnectionTypes\":[\"callback-polling\"]}]";
        String encodedMsg = URLEncoder.encode(msg, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/cometd/handshake?jsonp=cb&message=" + encodedMsg))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> sendLongPollingHandshake(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cometd/handshake"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "[{\"channel\":\"/meta/handshake\",\"version\":\"1.0\",\"supportedConnectionTypes\":[\"long-polling\"]}]"))
                .header("Content-Type", "application/json")
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("cometd://127.0.0.1:" + defaultTransportsPort.getPort() + "/service/test")
                        .to("mock:default");
                from("cometd://127.0.0.1:" + customTransportsPort.getPort()
                     + "/service/custom?allowedTransports=websocket, long-polling, callback-polling")
                        .to("mock:custom");
            }
        };
    }
}
