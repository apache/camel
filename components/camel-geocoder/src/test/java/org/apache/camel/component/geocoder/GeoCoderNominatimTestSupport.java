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
package org.apache.camel.component.geocoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.test.junit6.CamelTestSupport;

/**
 * Runs the Nominatim tests against a local HTTP server that answers the recorded responses of
 * nominatim.openstreetmap.org, so the build does not depend on the public service.
 */
public abstract class GeoCoderNominatimTestSupport extends CamelTestSupport {

    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String answer = answers.get(exchange.getRequestURI().getPath());
            byte[] bytes = answer != null ? answer.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(answer != null ? 200 : 404, bytes.length > 0 ? bytes.length : -1);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * The URL of the local Nominatim server, for the serverUrl option.
     */
    protected String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Sets the answer of the given operation (search or reverse).
     */
    protected void answer(String operation, String json) {
        answers.put("/" + operation, json);
    }

    /**
     * Sets the answer of the given operation (search or reverse) from a recorded response in
     * src/test/resources/nominatim.
     */
    protected void answerFromResource(String operation, String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/nominatim/" + name)) {
            answer(operation, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
