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
package org.apache.camel.component.atmosphere.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The open, close and error events of a connection carry the query parameters of the request that opened that
 * connection.
 */
public class WebsocketEventQueryParameterTest extends WebsocketCamelRouterWithInitParamTestSupport {

    @Test
    void openEventHasQueryParametersOfItsConnection() {
        MockEndpoint mock = getMockEndpoint("mock:events");

        WebsocketTestClient clientA = connect("?client=a");
        awaitEvents(mock, 1);
        WebsocketTestClient clientB = connect("?client=b");
        awaitEvents(mock, 2);

        Message openA = event(mock, WebsocketConstants.ONOPEN_EVENT_TYPE, 0);
        Message openB = event(mock, WebsocketConstants.ONOPEN_EVENT_TYPE, 1);
        assertEquals("a", openA.getHeader("client"));
        assertEquals("b", openB.getHeader("client"));
        assertNotEquals(openA.getHeader(WebsocketConstants.CONNECTION_KEY), openB.getHeader(WebsocketConstants.CONNECTION_KEY));

        clientA.close();
        clientB.close();
    }

    @Test
    void closeEventHasQueryParametersOfItsConnection() {
        MockEndpoint mock = getMockEndpoint("mock:events");

        WebsocketTestClient clientA = connect("?client=a");
        awaitEvents(mock, 1);
        WebsocketTestClient clientB = connect("?client=b");
        awaitEvents(mock, 2);
        clientA.close();
        awaitEvents(mock, 3);

        Message openA = event(mock, WebsocketConstants.ONOPEN_EVENT_TYPE, 0);
        Message closeA = event(mock, WebsocketConstants.ONCLOSE_EVENT_TYPE, 0);
        assertEquals(openA.getHeader(WebsocketConstants.CONNECTION_KEY), closeA.getHeader(WebsocketConstants.CONNECTION_KEY));
        assertEquals("a", closeA.getHeader("client"));

        clientB.close();
    }

    @Test
    void plainHttpRequestDoesNotChangeEventHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:events");

        WebsocketTestClient clientA = connect("?client=a");
        awaitEvents(mock, 1);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + "/events?client=other"))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        clientA.close();
        awaitEvents(mock, 2);

        Message closeA = event(mock, WebsocketConstants.ONCLOSE_EVENT_TYPE, 0);
        assertEquals("a", closeA.getHeader("client"));
    }

    @Test
    void queryParameterValuesAreUrlDecoded() {
        MockEndpoint mock = getMockEndpoint("mock:events");

        WebsocketTestClient client = connect("?name=John+Doe&city=S%C3%A3o+Paulo");
        awaitEvents(mock, 1);

        Message open = event(mock, WebsocketConstants.ONOPEN_EVENT_TYPE, 0);
        assertEquals("John Doe", open.getHeader("name"));
        assertEquals("S\u00e3o Paulo", open.getHeader("city"));

        client.close();
    }

    private static WebsocketTestClient connect(String query) {
        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + PORT + "/events" + query);
        client.connect();
        return client;
    }

    private static void awaitEvents(MockEndpoint mock, int count) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= count);
    }

    private static Message event(MockEndpoint mock, int eventType, int index) {
        List<Message> events = mock.getReceivedExchanges().stream()
                .map(Exchange::getIn)
                .filter(m -> Integer.valueOf(eventType).equals(m.getHeader(WebsocketConstants.EVENT_TYPE)))
                .toList();
        return events.get(index);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("atmosphere-websocket:///events").to("mock:events");
            }
        };
    }
}
