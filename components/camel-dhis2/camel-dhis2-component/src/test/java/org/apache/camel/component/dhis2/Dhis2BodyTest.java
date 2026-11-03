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
package org.apache.camel.component.dhis2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Sends the producer bodies to a local HTTP server that stands in for the DHIS2 Web API, and polls a collection from
 * it.
 */
public class Dhis2BodyTest extends CamelTestSupport {

    private static final String JSON = "{\"name\":\"Acme\",\"shortName\":\"Acme\"}";

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private HttpServer server;

    @TempDir
    Path tempDir;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api", this::handle);
        server.start();

        CamelContext context = super.createCamelContext();
        Dhis2Configuration configuration = new Dhis2Configuration();
        configuration.setBaseApiUrl("http://localhost:" + server.getAddress().getPort() + "/api");
        configuration.setUsername("admin");
        configuration.setPassword("district");
        Dhis2Component component = new Dhis2Component(context);
        component.setConfiguration(configuration);
        context.addComponent("dhis2", component);
        return context;
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);

        String response;
        if (exchange.getRequestURI().getPath().endsWith("/organisationUnits")) {
            response = "{\"organisationUnits\":[{\"id\":\"a\",\"name\":\"A\"},{\"id\":\"b\",\"name\":\"B\"}]}";
        } else {
            response = "{\"status\":\"OK\"}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("dhis2:get/collection?path=organisationUnits&arrayName=organisationUnits&delay=60000")
                        .routeId("poll").autoStartup(false)
                        .to("mock:resources");
            }
        };
    }

    private String lastRequest() {
        return requests.get(requests.size() - 1);
    }

    @Test
    void postSendsTextAsItIs() {
        template.requestBody("dhis2:post/resource?path=organisationUnits/x", JSON);
        assertEquals("POST /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void postSendsBytesAsJson() {
        template.requestBody("dhis2:post/resource?path=organisationUnits/x", JSON.getBytes(StandardCharsets.UTF_8));
        assertEquals("POST /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void putSendsStreamAsJson() {
        template.requestBody("dhis2:put/resource?path=organisationUnits/x",
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)));
        assertEquals("PUT /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void putSendsFileAsJson() throws IOException {
        File file = Files.writeString(tempDir.resolve("unit.json"), JSON).toFile();
        template.requestBody("dhis2:put/resource?path=organisationUnits/x", file);
        assertEquals("PUT /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void postSendsMapAsJson() {
        template.requestBody("dhis2:post/resource?path=organisationUnits/x", Map.of("name", "Acme"));
        assertEquals("POST /api/organisationUnits/x {\"name\":\"Acme\"}", lastRequest());
    }

    @Test
    void deleteDoesNotSendLeftoverBody() {
        template.requestBody("dhis2:delete/resource?path=organisationUnits/x", JSON);
        assertEquals("DELETE /api/organisationUnits/x ", lastRequest());
    }

    @Test
    void deleteSendsBodyGivenByInBody() {
        template.requestBody("dhis2:delete/resource?path=organisationUnits/x&inBody=resource",
                JSON.getBytes(StandardCharsets.UTF_8));
        assertEquals("DELETE /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void deleteSendsResourceGivenByHeader() {
        template.requestBodyAndHeader("dhis2:delete/resource?path=organisationUnits/x", null, "CamelDhis2.resource",
                JSON);
        assertEquals("DELETE /api/organisationUnits/x " + JSON, lastRequest());
    }

    @Test
    void collectionConsumerSendsOneMessagePerResource() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resources");
        mock.expectedMessageCount(2);

        context.getRouteController().startRoute("poll");

        mock.assertIsSatisfied();
        Dhis2Resource first = assertInstanceOf(Dhis2Resource.class, mock.getExchanges().get(0).getIn().getBody());
        Dhis2Resource second = assertInstanceOf(Dhis2Resource.class, mock.getExchanges().get(1).getIn().getBody());
        assertEquals("A", first.get("name"));
        assertEquals("B", second.get("name"));
    }
}
