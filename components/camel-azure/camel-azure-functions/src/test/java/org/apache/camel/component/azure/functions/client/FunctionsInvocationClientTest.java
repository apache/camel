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
package org.apache.camel.component.azure.functions.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient.FunctionInvocationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The invocation client against a JDK HttpServer that stands in for the function app.
 */
class FunctionsInvocationClientTest {

    private final List<String> functionKeys = new CopyOnWriteArrayList<>();
    private HttpServer server;

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
    void functionKeyIsOnlyAddedForHttpsFunctionAppUrls() throws Exception {
        // the fake function app is on a plain http URL of the local host
        FunctionsInvocationClient client = new FunctionsInvocationClient("secret", null, 10000, 10000);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/myfunction";

        FunctionInvocationResponse response = client.invoke(url, "POST", "Hello", Map.of());

        assertEquals(200, response.getStatusCode());
        assertEquals(1, functionKeys.size());
        assertNull(functionKeys.get(0));
    }

    private void handle(HttpExchange http) throws IOException {
        try (InputStream in = http.getRequestBody()) {
            in.readAllBytes();
        }
        functionKeys.add(http.getRequestHeaders().getFirst("x-functions-key"));

        byte[] answer = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        http.sendResponseHeaders(200, answer.length);
        try (OutputStream out = http.getResponseBody()) {
            out.write(answer);
        }
    }
}
