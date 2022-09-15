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
package org.apache.camel.component.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketStaticTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(WebsocketStaticTest.class);

    private int port;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
    }

    @Test
    public void testStaticResource() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://127.0.0.1:" + port + "/echo", 1);
        testClient.connect();

        testClient.sendTextMessage("Beer");
        assertTrue(testClient.await(10, TimeUnit.SECONDS));

        assertEquals(1, testClient.getReceived().size());
        assertEquals("BeerBeer", testClient.getReceived().get(0));

        // now call static html
        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/hello.html"))
                .GET()
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertNotNull(response);
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertNotNull(body);
        LOG.info(body);
        assertTrue(body.contains("Hello World"));

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setPort(port);
                websocketComponent.setMinThreads(1);
                websocketComponent.setMaxThreads(25);
                websocketComponent.setStaticResources("classpath:.");

                // START SNIPPET: e1
                // expose a echo websocket client, that sends back an echo
                from("websocket://echo")
                        .log(">>> Message received from WebSocket Client : ${body}")
                        .transform().simple("${body}${body}")
                        // send back to the client, by sending the message to the same endpoint
                        // this is needed as by default messages is InOnly
                        // and we will by default send back to the current client using the provided connection key
                        .to("websocket://echo");
                // END SNIPPET: e1
            }
        };
    }
}
