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
package org.apache.camel.component.cyberark.vault.client.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.component.cyberark.vault.client.ConjurClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The version of the secret is sent as one encoded query parameter.
 */
class ConjurClientSecretVersionTest {

    private final List<String> rawQueries = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private String url;

    @BeforeEach
    void startConjur() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/secrets/", exchange -> {
            rawQueries.add(String.valueOf(exchange.getRequestURI().getRawQuery()));
            byte[] body = "secret-value".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopConjur() {
        server.stop(0);
    }

    @Test
    void numericVersion() throws Exception {
        try (ConjurClient client = ConjurClientFactory.createWithToken(url, "myaccount", "my-token")) {
            assertEquals("secret-value", client.retrieveSecret("db/password", "3"));
        }
        assertEquals(List.of("version=3"), rawQueries);
    }

    @Test
    void versionWithReservedCharactersIsEncoded() throws Exception {
        try (ConjurClient client = ConjurClientFactory.createWithToken(url, "myaccount", "my-token")) {
            assertEquals("secret-value", client.retrieveSecret("db/password", "1&other=2"));
        }
        assertEquals(List.of("version=1%26other%3D2"), rawQueries);
    }

    @Test
    void versionWithSpaceIsEncoded() throws Exception {
        try (ConjurClient client = ConjurClientFactory.createWithToken(url, "myaccount", "my-token")) {
            assertEquals("secret-value", client.retrieveSecret("db/password", "1 2"));
        }
        assertEquals(List.of("version=1+2"), rawQueries);
    }

    @Test
    void noVersion() throws Exception {
        try (ConjurClient client = ConjurClientFactory.createWithToken(url, "myaccount", "my-token")) {
            assertEquals("secret-value", client.retrieveSecret("db/password"));
        }
        assertEquals(List.of("null"), rawQueries);
    }
}
