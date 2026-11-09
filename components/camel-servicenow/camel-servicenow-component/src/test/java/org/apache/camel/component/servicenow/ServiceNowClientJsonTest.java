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
package org.apache.camel.component.servicenow;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends requests through the JSON provider of the JAX-RS client, against a local server.
 */
class ServiceNowClientJsonTest {

    private final CamelContext context = new DefaultCamelContext();
    private HttpServer server;
    private volatile String requestBody;
    private volatile String responseBody = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private ServiceNowConfiguration configuration() {
        ServiceNowConfiguration configuration = new ServiceNowConfiguration();
        configuration.setApiUrl("http://localhost:" + server.getAddress().getPort());
        configuration.setUserName("user");
        configuration.setPassword("password");
        return configuration;
    }

    public static class Record {
        public LocalDate date = LocalDate.of(1990, 5, 17);
        public String empty;
    }

    @Test
    void requestUsesTheMapperOfTheConfiguration() throws Exception {
        ServiceNowConfiguration configuration = configuration();
        configuration.setDateFormat("dd/MM/yyyy");
        ServiceNowClient client = new ServiceNowClient(context, configuration);

        client.types(MediaType.APPLICATION_JSON_TYPE).invoke(HttpMethod.POST, new Record());

        assertEquals("{\"date\":\"17/05/1990\"}", requestBody);
    }

    @Test
    void responseIsReadAsJsonNode() throws Exception {
        responseBody = "{\"result\":{\"sys_id\":\"1\"}}";
        ServiceNowClient client = new ServiceNowClient(context, configuration());

        Response response = client.types(MediaType.APPLICATION_JSON_TYPE).invoke(HttpMethod.GET);

        assertEquals("1", response.readEntity(JsonNode.class).get("result").get("sys_id").asString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseAttributesThatAreNotStringsDoNotFail() throws Exception {
        responseBody = "{\"result\":{\"sys_id\":\"1\"},\"name\":\"value\",\"count\":3,\"none\":null}";
        ServiceNowConfiguration configuration = configuration();
        ServiceNowClient client = new ServiceNowClient(context, configuration);
        ServiceNowEndpoint endpoint = new ServiceNowEndpoint("servicenow:test", null, configuration, "test");
        endpoint.setCamelContext(context);
        AbstractServiceNowProcessor processor = new AbstractServiceNowProcessor(endpoint) {
        };
        Message message = new DefaultMessage(context);

        processor.setBody(message, Map.class, client.types(MediaType.APPLICATION_JSON_TYPE).invoke(HttpMethod.GET));

        assertEquals(Map.of("sys_id", "1"), message.getBody());
        Map<String, String> attributes = message.getHeader(ServiceNowConstants.RESPONSE_META, Map.class);
        assertEquals("value", attributes.get("name"));
        assertTrue(attributes.containsKey("count"));
        assertNull(attributes.get("count"));
        assertTrue(attributes.containsKey("none"));
        assertNull(attributes.get("none"));
    }

    @Test
    void mapperMustBeAJsonMapper() {
        ServiceNowConfiguration configuration = configuration();
        configuration.setMapper(new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> new ServiceNowClient(context, configuration));
    }
}
