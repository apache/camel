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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketRouteWithInitParamTest extends WebsocketCamelRouterWithInitParamTestSupport {

    private static final String[] EXISTED_USERS = { "Kim", "Pavlo", "Peter" };
    private static String[] broadcastMessageTo = {};
    private static Map<String, String> connectionKeyUserMap = new HashMap<>();

    private void runtTest(String s) throws InterruptedException, ExecutionException, IOException {
        WebsocketTestClient wsclient = new WebsocketTestClient("ws://localhost:" + PORT + s);
        wsclient.connect();
        wsclient.close();
    }

    @Test
    void testWebsocketEventsResendingEnabled() {
        assertDoesNotThrow(() -> runtTest("/hola"));

    }

    @Test
    void testPassParametersWebsocketOnOpen() {
        assertDoesNotThrow(() -> runtTest("/hola1?param1=value1&param2=value2"));
    }

    @Test
    void testWebsocketSingleClientBroadcastMultipleClients() throws Exception {
        final int awaitTime = 5;
        connectionKeyUserMap.clear();

        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + PORT + "/broadcast", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + PORT + "/broadcast", 2);
        WebsocketTestClient wsclient3 = new WebsocketTestClient("ws://localhost:" + PORT + "/broadcast", 2);

        wsclient1.connect();
        wsclient1.await(awaitTime);

        wsclient2.connect();
        wsclient2.await(awaitTime);

        wsclient3.connect();
        wsclient3.await(awaitTime);

        //all connections were registered in external store
        assertEquals(EXISTED_USERS.length, connectionKeyUserMap.size());

        broadcastMessageTo = new String[] { EXISTED_USERS[0], EXISTED_USERS[1] };

        wsclient1.sendTextMessage("Gambas");
        wsclient1.await(awaitTime);

        List<String> received1 = wsclient1.getReceived(String.class);
        assertEquals(1, received1.size());

        for (String element : broadcastMessageTo) {
            assertTrue(received1.get(0).contains(element));
        }

        List<String> received2 = wsclient2.getReceived(String.class);
        assertEquals(1, received2.size());
        for (String element : broadcastMessageTo) {
            assertTrue(received2.get(0).contains(element));
        }

        List<String> received3 = wsclient3.getReceived(String.class);
        assertEquals(0, received3.size());

        wsclient1.close();
        wsclient2.close();
        wsclient3.close();
    }

    @Test
    void testWebsocketSingleClientBroadcastMultipleClientsGuaranteeDelivery() throws Exception {
        final int awaitTime = 5;
        connectionKeyUserMap.clear();

        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);
        WebsocketTestClient wsclient3 = new WebsocketTestClient("ws://localhost:" + PORT + "/guarantee", 2);

        wsclient1.connect();
        wsclient1.await(awaitTime);

        wsclient2.connect();
        wsclient2.await(awaitTime);

        wsclient3.connect();
        wsclient3.await(awaitTime);

        //all connections were registered in external store
        assertEquals(EXISTED_USERS.length, connectionKeyUserMap.size());

        wsclient2.close();
        wsclient2.await(awaitTime);

        broadcastMessageTo = new String[] { EXISTED_USERS[0], EXISTED_USERS[1] };

        wsclient1.sendTextMessage("Gambas");
        wsclient1.await(awaitTime);

        List<String> received1 = wsclient1.getReceived(String.class);
        assertEquals(1, received1.size());

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
                // route for events resending enabled
                from("atmosphere-websocket:///hola").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) {
                        checkEventsResendingEnabled(exchange);
                    }
                });

                // route for events resending enabled
                from("atmosphere-websocket:///hola1").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) {
                        checkPassedParameters(exchange);
                    }
                });

                // route for single client broadcast to multiple clients
                from("atmosphere-websocket:///broadcast").to("log:info")
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
                        .otherwise()
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                createBroadcastMultipleClientsResponse(exchange);
                            }
                        }).to("atmosphere-websocket:///broadcast");

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

        if (connectionKeyUserMap.size() > 0) {
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

    private static void checkEventsResendingEnabled(Exchange exchange) {
        Object connectionKey = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY);
        Object eventType = exchange.getIn().getHeader(WebsocketConstants.EVENT_TYPE);
        Object msg = exchange.getIn().getBody();

        assertNull(msg);
        assertNotNull(connectionKey);

        if (eventType instanceof Integer) {
            assertTrue(eventType.equals(WebsocketConstants.ONOPEN_EVENT_TYPE)
                    || eventType.equals(WebsocketConstants.ONCLOSE_EVENT_TYPE)
                    || eventType.equals(WebsocketConstants.ONERROR_EVENT_TYPE));
        }
    }

    private static void checkPassedParameters(Exchange exchange) {
        Object connectionKey = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY);
        Object eventType = exchange.getIn().getHeader(WebsocketConstants.EVENT_TYPE);
        Object msg = exchange.getIn().getBody();

        assertNull(msg);
        assertNotNull(connectionKey);

        if (eventType instanceof Integer && eventType.equals(WebsocketConstants.ONOPEN_EVENT_TYPE)) {

            String param1 = (String) exchange.getIn().getHeader("param1");
            String param2 = (String) exchange.getIn().getHeader("param2");

            assertTrue(param1.equals("value1") && param2.equals("value2"));
        }
    }
    // END SNIPPET: payload
}
