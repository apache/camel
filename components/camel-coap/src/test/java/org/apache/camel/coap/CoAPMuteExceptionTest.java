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
package org.apache.camel.coap;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoAPMuteExceptionTest extends CoAPTestSupport {

    private static final String ROUTE_EXCEPTION_MESSAGE = "route failure detail";
    private static final String RESPONSE_EXCEPTION_MESSAGE = "response body detail";

    @Test
    void routeFailureIsAnsweredWithAnEmptyServerErrorByDefault() throws Exception {
        CoapResponse response = createClient("/failure").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals(0, response.getPayload().length,
                "the response must carry neither the exception nor the body of the failed exchange");
    }

    @Test
    void routeFailureCarriesTheExceptionMessageWhenNotMuted() throws Exception {
        CoapResponse response = createClient("/failureNotMuted").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals(ROUTE_EXCEPTION_MESSAGE, response.getResponseText());
    }

    @Test
    void responseBuildingFailureIsAnsweredWithAnEmptyServerErrorByDefault() throws Exception {
        CoapResponse response = createClient("/unreadableBody").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals(0, response.getPayload().length, "the response must not carry the exception's message");
    }

    @Test
    void responseBuildingFailureCarriesTheExceptionMessageWhenNotMuted() throws Exception {
        CoapResponse response = createClient("/unreadableBodyNotMuted").get();

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertTrue(response.getResponseText().contains(RESPONSE_EXCEPTION_MESSAGE),
                "expected the exception's message with muteException=false: " + response.getResponseText());
    }

    @Test
    void successfulExchangeIsAnsweredWithContent() throws Exception {
        CoapResponse response = createClient("/success").get();

        assertEquals(ResponseCode.CONTENT, response.getCode());
        assertEquals("ok", response.getResponseText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/failure", PORT)
                        .setBody(constant("partial result"))
                        .throwException(IllegalStateException.class, ROUTE_EXCEPTION_MESSAGE);

                fromF("coap://localhost:%d/failureNotMuted?muteException=false", PORT)
                        .setBody(constant("partial result"))
                        .throwException(IllegalStateException.class, ROUTE_EXCEPTION_MESSAGE);

                fromF("coap://localhost:%d/unreadableBody", PORT)
                        .process(exchange -> exchange.getMessage().setBody(new UnreadableStream()));

                fromF("coap://localhost:%d/unreadableBodyNotMuted?muteException=false", PORT)
                        .process(exchange -> exchange.getMessage().setBody(new UnreadableStream()));

                fromF("coap://localhost:%d/success", PORT)
                        .setBody(constant("ok"));
            }
        };
    }

    /**
     * A body that cannot be converted to the response payload, so the failure happens after the route completed.
     */
    private static final class UnreadableStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException(RESPONSE_EXCEPTION_MESSAGE);
        }
    }
}
