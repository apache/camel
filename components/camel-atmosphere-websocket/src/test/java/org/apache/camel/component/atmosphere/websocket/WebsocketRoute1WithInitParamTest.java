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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

public class WebsocketRoute1WithInitParamTest extends WebsocketCamelRouterWithInitParamTestSupport {

    private void runtTest(String s) {
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
            }
        };
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
