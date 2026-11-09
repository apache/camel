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
package org.apache.camel.component.whatsapp;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.whatsapp.model.ContactMessage;
import org.apache.camel.component.whatsapp.model.ContactMessageRequest;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON sent to the API: dates in the format of the model, and no null fields.
 */
class WhatsAppRequestJsonTest extends CamelTestSupport {

    private HttpServer server;
    private volatile String requestBody;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @Override
    protected void cleanupResources() {
        server.stop(0);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        WhatsAppComponent component = new WhatsAppComponent();
        component.setAuthorizationToken("token");
        context.addComponent("whatsapp", component);
        return context;
    }

    @Test
    void writesDatesAsTheModelSaysAndLeavesOutNulls() throws Exception {
        ContactMessage contact = new ContactMessage();
        contact.setBirthday(LocalDate.of(1990, 5, 17));
        ContactMessageRequest request = new ContactMessageRequest();
        request.setTo("15550001111");
        request.setContacts(List.of(contact));

        Exchange exchange = template.asyncSend("direct:start", e -> e.getIn().setBody(request)).get(30, TimeUnit.SECONDS);

        assertThat(exchange.getException()).isNull();
        assertThat(requestBody).contains("\"contacts\":[{\"birthday\":\"1990-05-17\"}]").doesNotContain("null");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("whatsapp:123?baseUri=http://localhost:" + server.getAddress().getPort());
            }
        };
    }
}
