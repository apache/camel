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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A header with several values (a collection or an array, as the HTTP consumers map a repeated request header) must be
 * sent as one HTTP header per value, as camel-http, camel-netty-http and camel-undertow do.
 */
class VertxHttpMultiValueHeaderTest extends VertxHttpTestSupport {

    @Test
    void sendsEachValueOfAListHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, "X-Test", List.of("a", "b"), String.class);
        assertEquals("a|b", result);
    }

    @Test
    void sendsEachValueOfAnArrayHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, "X-Test", new String[] { "a", "b" },
                String.class);
        assertEquals("a|b", result);
    }

    @Test
    void sendsAStringHeaderWithACommaAsOneValue() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, "X-Test", "a, b", String.class);
        assertEquals("a, b", result);
    }

    @Test
    void sendsAByteArrayHeaderAsOneValue() {
        // a byte[] (such as a Kafka record header) is one value and not one header per byte
        byte[] value = "abc".getBytes(StandardCharsets.UTF_8);
        String result = template.requestBodyAndHeader(getProducerUri(), null, "X-Test", value, String.class);
        assertEquals("abc", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the undertow consumer maps a repeated request header to a list of its values
                from(getTestServerUri())
                        .process(exchange -> {
                            Object value = exchange.getMessage().getHeader("X-Test");
                            String body = value instanceof List<?> list
                                    ? String.join("|", list.stream().map(String::valueOf).toList())
                                    : String.valueOf(value);
                            exchange.getMessage().setBody(body);
                        });
            }
        };
    }
}
