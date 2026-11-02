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
package org.apache.camel.component.zendesk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.component.zendesk.internal.ZendeskApiName;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zendesk.client.v2.model.Ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The zendesk component has a single API, so the API name in the path is optional: zendesk:methodName and
 * zendesk:default/methodName are the same endpoint.
 */
public class ZendeskApiNameTest extends CamelTestSupport {

    private HttpServer server;

    @Override
    protected void doPreSetup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v2/tickets/42.json", this::ticket);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void ticket(HttpExchange exchange) throws IOException {
        byte[] json = "{\"ticket\":{\"id\":42,\"subject\":\"Hello Camel\"}}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json);
        }
    }

    private String options() {
        return "?inBody=id&serverUrl=http://localhost:" + server.getAddress().getPort() + "&username=camel&password=secret";
    }

    @Test
    public void testMethodNameOnly() {
        ZendeskEndpoint endpoint = context.getEndpoint("zendesk:getTicket" + options(), ZendeskEndpoint.class);
        assertEquals(ZendeskApiName.DEFAULT, endpoint.getApiName());
        assertEquals("getTicket", endpoint.getMethodName());

        Ticket ticket = template.requestBody(endpoint, 42L, Ticket.class);
        assertEquals(42L, ticket.getId());
        assertEquals("Hello Camel", ticket.getSubject());
    }

    @Test
    public void testDefaultApiName() {
        ZendeskEndpoint endpoint = context.getEndpoint("zendesk:default/getTicket" + options(), ZendeskEndpoint.class);
        assertEquals(ZendeskApiName.DEFAULT, endpoint.getApiName());
        assertEquals("getTicket", endpoint.getMethodName());

        Ticket ticket = template.requestBody(endpoint, 42L, Ticket.class);
        assertEquals(42L, ticket.getId());
        assertEquals("Hello Camel", ticket.getSubject());
    }

    @Test
    public void testUnknownApiName() {
        Exception e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("zendesk:unknown/getTicket" + options()));
        assertInstanceOf(TypeConversionException.class, e.getCause());
    }
}
