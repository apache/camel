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
package org.apache.camel.component.stream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StreamRouteBuilderTest extends CamelTestSupport {

    @Test
    void testStringContent() {
        String body = "this is text\n";
        Exchange result = template.send("direct:start", exchange -> exchange.getIn().setBody(body));
        assertNotNull(result);
        assertFalse(result.isFailed(), "Sending string content should not cause an exchange failure");
        assertEquals(body, result.getIn().getBody(String.class), "Body content should be preserved after sending");
    }

    @Test
    void testBinaryContent() {
        byte[] body = "This is bytes\n".getBytes();
        Exchange result = template.send("direct:start", exchange -> exchange.getIn().setBody(body));
        assertNotNull(result);
        assertFalse(result.isFailed(), "Sending binary content should not cause an exchange failure");
        assertArrayEquals(body, result.getIn().getBody(byte[].class), "Binary body content should be preserved after sending");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader("stream", constant(System.out))
                        .to("stream:err", "stream:out", "stream:header");
            }
        };
    }

}
