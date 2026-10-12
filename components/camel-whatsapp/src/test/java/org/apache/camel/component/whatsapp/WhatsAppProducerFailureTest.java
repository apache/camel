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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.whatsapp.model.MessageResponse;
import org.apache.camel.component.whatsapp.model.TextMessage;
import org.apache.camel.component.whatsapp.model.TextMessageRequest;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The producer must complete the exchange for every outcome of the HTTP request: a response, an error response, and no
 * response at all.
 */
class WhatsAppProducerFailureTest extends CamelTestSupport {

    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    // nothing listens on this port
    private final int closedPort = AvailablePortFinder.getNextAvailable();

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
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
    void failsTheExchangeWhenTheConnectionFails() throws Exception {
        // an asynchronous send, so that an exchange that is never completed fails the test instead of blocking it
        Exchange exchange = template.asyncSend("direct:unreachable", e -> e.getIn().setBody(textMessage()))
                .get(30, TimeUnit.SECONDS);

        // the exception of the HTTP client (a ConnectException without a message) is the cause, and the message says
        // where the request went
        assertThat(exchange.getException()).isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Could not send message to http://localhost:" + closedPort)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void failsTheExchangeForAnErrorResponse() throws Exception {
        status = 400;
        responseBody = "{\"error\":{\"message\":\"Unsupported post request\",\"code\":100}}";

        Exchange exchange = template.asyncSend("direct:server", e -> e.getIn().setBody(textMessage()))
                .get(30, TimeUnit.SECONDS);

        assertThat(exchange.getException()).isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("http://localhost:" + server.getAddress().getPort())
                .hasMessageContaining("responded: 400").hasMessageContaining("Unsupported post request");
    }

    @Test
    void returnsTheResponse() throws Exception {
        responseBody = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.1\"}]}";

        Exchange exchange = template.asyncSend("direct:server", e -> e.getIn().setBody(textMessage()))
                .get(30, TimeUnit.SECONDS);

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isInstanceOf(MessageResponse.class);
    }

    private static TextMessageRequest textMessage() {
        TextMessageRequest request = new TextMessageRequest();
        request.setTo("15550001111");
        request.setText(new TextMessage());
        request.getText().setBody("Hello");
        return request;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unreachable").to("whatsapp:123?baseUri=http://localhost:" + closedPort);
                from("direct:server").to("whatsapp:123?baseUri=http://localhost:" + server.getAddress().getPort());
            }
        };
    }
}
