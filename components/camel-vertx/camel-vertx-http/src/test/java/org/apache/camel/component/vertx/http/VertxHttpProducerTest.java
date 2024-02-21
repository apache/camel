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
package org.apache.camel.component.vertx.http;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExchangeHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpProducerTest extends VertxHttpTestSupport {

    @Test
    public void testVertxHttpProducer() {
        String expectedBody = "Hello World";

        Exchange exchange = template.request(getProducerUri(), null);

        Message message = exchange.getMessage();
        Map<String, Object> headers = message.getHeaders();
        assertTrue(headers.containsKey("Connection"));
        assertTrue(headers.containsKey("Content-Length"));
        assertTrue(headers.containsKey("user-agent"));
        assertEquals(String.valueOf(expectedBody.length()), headers.get(Exchange.CONTENT_LENGTH));
        assertEquals(200, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", headers.get(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals(expectedBody, message.getBody(String.class));
    }

    @Test
    public void testVertxHttpProducerWithContentType() {
        String expectedBody = "{\"foo\": \"bar\"}";

        Exchange exchange = template.request(getProducerUri() + "/content/type", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json; charset=iso-8859-4");
            }
        });

        Message message = exchange.getMessage();
        Map<String, Object> headers = message.getHeaders();
        assertTrue(headers.containsKey("Connection"));
        assertTrue(headers.containsKey("Content-Length"));
        assertTrue(headers.containsKey("user-agent"));
        assertEquals(String.valueOf(expectedBody.length()), headers.get(Exchange.CONTENT_LENGTH));
        assertEquals(200, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", headers.get(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals(expectedBody, message.getBody(String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(constant("Hello World"));

                from(getTestServerUri() + "/content/type")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                String contentType = ExchangeHelper.getContentType(exchange);
                                if (!contentType.startsWith("application/json")) {
                                    throw new IllegalStateException("Unexpected Content-Type header");
                                }

                                Message message = exchange.getMessage();
                                message.setBody("{\"foo\": \"bar\"}");
                            }
                        });
            }
        };
    }
}
