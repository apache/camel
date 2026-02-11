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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketRoute2Test extends WebsocketCamelRouterTestSupport {
    private static final String RESPONSE_GREETING = "Hola ";
    private static final byte[] RESPONSE_GREETING_BYTES = { 0x48, 0x6f, 0x6c, 0x61, 0x20 };

    @Test
    void testWebsocketBroadcastClient() throws Exception {
        WebsocketTestClient wsclient1 = new WebsocketTestClient("ws://localhost:" + PORT + "/broadcast", 2);
        WebsocketTestClient wsclient2 = new WebsocketTestClient("ws://localhost:" + PORT + "/broadcast", 2);
        wsclient1.connect();
        wsclient2.connect();

        wsclient1.sendTextMessage("Gambas");
        wsclient2.sendTextMessage("Calamares");

        assertTrue(wsclient1.await(10));
        assertTrue(wsclient2.await(10));

        List<String> received1 = wsclient1.getReceived(String.class);
        assertEquals(2, received1.size());

        assertTrue(received1.contains("Hola Gambas"));
        assertTrue(received1.contains("Hola Calamares"));

        List<String> received2 = wsclient2.getReceived(String.class);
        assertEquals(2, received2.size());
        assertTrue(received2.contains("Hola Gambas"));
        assertTrue(received2.contains("Hola Calamares"));

        wsclient1.close();
        wsclient2.close();
    }

    // START SNIPPET: payload
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // route for a broadcast line
                from("atmosphere-websocket:///broadcast").to("log:info").process(new Processor() {
                    public void process(final Exchange exchange) {
                        createResponse(exchange);
                    }
                }).to("atmosphere-websocket:///broadcast?sendToAll=true");
            }
        };
    }

    private static void createResponse(Exchange exchange) {
        Object msg = exchange.getIn().getBody();
        assertTrue(msg instanceof String || msg instanceof byte[] || msg instanceof Reader || msg instanceof InputStream,
                "Expects String, byte[], Reader or InputStream");

        if (msg instanceof String) {
            exchange.getIn().setBody(RESPONSE_GREETING + msg);
        } else if (msg instanceof byte[]) {
            exchange.getIn().setBody(createByteResponse((byte[]) msg));
        } else if (msg instanceof Reader) {
            exchange.getIn().setBody(RESPONSE_GREETING + readAll((Reader) msg));
        } else if (msg instanceof InputStream) {
            exchange.getIn().setBody(createByteResponse(readAll((InputStream) msg)));
        }
    }

    private static void checkEventsResendingDisabled(Exchange exchange) {
        Object eventType = exchange.getIn().getHeader(WebsocketConstants.EVENT_TYPE);

        if (eventType instanceof Integer) {
            if (eventType.equals(WebsocketConstants.ONOPEN_EVENT_TYPE)
                    || eventType.equals(WebsocketConstants.ONCLOSE_EVENT_TYPE)
                    || eventType.equals(WebsocketConstants.ONERROR_EVENT_TYPE)) {
                exchange.getIn().setBody("Error. This place should never be reached.");
            }
        }
    }

    private static byte[] createByteResponse(byte[] req) {
        byte[] resp = new byte[req.length + RESPONSE_GREETING_BYTES.length];
        System.arraycopy(RESPONSE_GREETING_BYTES, 0, resp, 0, RESPONSE_GREETING_BYTES.length);
        System.arraycopy(req, 0, resp, RESPONSE_GREETING_BYTES.length, req.length);
        return resp;
    }

    private static String readAll(Reader reader) {
        try {
            return IOHelper.toString(reader);
        } catch (IOException e) {
        }

        return "";
    }

    private static byte[] readAll(InputStream is) {
        ByteArrayOutputStream byteBuf = new ByteArrayOutputStream();

        try {
            is.transferTo(byteBuf);
        } catch (IOException e) {
            // ignore
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // ignore
            }
        }

        return byteBuf.toByteArray();
    }
    // END SNIPPET: payload
}
