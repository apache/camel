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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketRoute3WithInitParamTest extends WebsocketCamelRouterWithInitParamTestSupport {

    private static final String[] EXISTED_USERS = { "Kim", "Pavlo", "Peter" };
    private static String[] broadcastMessageTo = {};
    private static Map<String, String> connectionKeyUserMap = new HashMap<>();

    @Test
    void testWebsocketSingleClientBroadcastMultipleClientsGuaranteeDelivery() throws Exception {
        connectionKeyUserMap.clear();

        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);
        WebsocketTestClient wsclient3 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);

        wsclient1.connect();
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, connectionKeyUserMap.size()));

        wsclient2.connect();
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, connectionKeyUserMap.size()));

        wsclient3.connect();
        //all connections were registered in external store
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(EXISTED_USERS.length, connectionKeyUserMap.size()));

        wsclient2.close();
        // brief wait for the close event to be processed server-side
        Thread.sleep(500);

        broadcastMessageTo = new String[] { EXISTED_USERS[0], EXISTED_USERS[1] };

        wsclient1.sendTextMessage("Gambas");

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, wsclient1.getReceived(String.class).size()));

        List<String> received1 = wsclient1.getReceived(String.class);

        for (String element : broadcastMessageTo) {
            assertTrue(received1.get(0).contains(element));
        }

        List<String> received2 = wsclient2.getReceived(String.class);
        assertEquals(0, received2.size());

        List<String> received3 = wsclient3.getReceived(String.class);
        assertEquals(0, received3.size());

        wsclient1.close();
        wsclient3.close();
    }

    // START SNIPPET: payload
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // route for single client broadcast to multiple clients guarantee delivery
                from("atmosphere-websocket:///guarantee").to("log:info")
                        .choice()
                        .when(header(WebsocketConstants.EVENT_TYPE).isEqualTo(WebsocketConstants.ONOPEN_EVENT_TYPE))
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                createExternalConnectionRegister(exchange);
                            }
                        })
                        .when(header(WebsocketConstants.EVENT_TYPE).isEqualTo(WebsocketConstants.ONCLOSE_EVENT_TYPE))
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                removeExternalConnectionRegister();
                            }
                        })
                        .when(header(WebsocketConstants.EVENT_TYPE).isEqualTo(WebsocketConstants.ONERROR_EVENT_TYPE))
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                removeExternalConnectionRegister();
                            }
                        })
                        .when(header(WebsocketConstants.ERROR_TYPE).isEqualTo(WebsocketConstants.MESSAGE_NOT_SENT_ERROR_TYPE))
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                handleNotDeliveredMessage(exchange);
                            }
                        })
                        .otherwise()
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                createBroadcastMultipleClientsResponse(exchange);
                            }
                        }).to("atmosphere-websocket:///guarantee");
            }
        };
    }

    private static void handleNotDeliveredMessage(Exchange exchange) {
        List<String> connectionKeyList = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY_LIST, List.class);
        assertEquals(1, connectionKeyList.size());
        assertEquals(connectionKeyList.get(0), connectionKeyUserMap.get(broadcastMessageTo[1]));
    }

    private static void createExternalConnectionRegister(Exchange exchange) {
        Object connectionKey = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY);

        String userName = EXISTED_USERS[0];

        if (!connectionKeyUserMap.isEmpty()) {
            userName = EXISTED_USERS[connectionKeyUserMap.size()];
        }

        connectionKeyUserMap.put(userName, (String) connectionKey);
    }

    private static void removeExternalConnectionRegister() {
        // remove connectionKey from external store
    }

    private static void createBroadcastMultipleClientsResponse(Exchange exchange) {
        List<String> connectionKeyList = new ArrayList<>();
        Object msg = exchange.getIn().getBody();

        String additionalMessage = "";

        //send the message only to selected connections
        for (String element : broadcastMessageTo) {
            connectionKeyList.add(connectionKeyUserMap.get(element));
            additionalMessage += element + " ";
        }

        additionalMessage += " Received the message: ";

        exchange.getIn().setBody(additionalMessage + msg);
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY_LIST, connectionKeyList);
    }

    // END SNIPPET: payload
}
