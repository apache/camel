/**
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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class WebsocketRouteWithInitParamTest extends WebsocketCamelRouterWithInitParamTestSupport {

    @Test
    public void testWebsocketEventsResendingEnabled() throws Exception {
        TestClient wsclient = new TestClient("ws://localhost:" + PORT + "/hola");
        wsclient.connect();
        wsclient.close();
    }

    @Test
    public void testPassParametersWebsocketOnOpen() throws Exception {
        TestClient wsclient = new TestClient("ws://localhost:" + PORT + "/hola1?param1=value1&param2=value2");
        wsclient.connect();
        wsclient.close();
    }

    // START SNIPPET: payload
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // route for events resending enabled
                from("atmosphere-websocket:///hola").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        checkEventsResendingEnabled(exchange);
                    }
                });

                // route for events resending enabled with parameters from url
                from("atmosphere-websocket:///hola1").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
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

        assertEquals(null, msg);
        assertTrue(connectionKey != null);

        if (eventType instanceof Integer) {
            assertTrue(eventType.equals(WebsocketConstants.ONOPEN_EVENT_TYPE) || eventType.equals(WebsocketConstants.ONCLOSE_EVENT_TYPE) || eventType.equals(WebsocketConstants.ONERROR_EVENT_TYPE));
        }
    }

    private static void checkPassedParameters(Exchange exchange) {
        Object connectionKey = exchange.getIn().getHeader(WebsocketConstants.CONNECTION_KEY);
        Object eventType = exchange.getIn().getHeader(WebsocketConstants.EVENT_TYPE);
        Object msg = exchange.getIn().getBody();

        assertEquals(null, msg);
        assertTrue(connectionKey != null);

        if ((eventType instanceof Integer) && eventType.equals(WebsocketConstants.ONOPEN_EVENT_TYPE)) {

            String param1 = (String)exchange.getIn().getHeader("param1");
            String param2 = (String)exchange.getIn().getHeader("param2");

            assertTrue(param1.equals("value1") && param2.equals("value2"));
        }
    }
    // END SNIPPET: payload
}
