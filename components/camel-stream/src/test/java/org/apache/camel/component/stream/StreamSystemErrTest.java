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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for System.err
 */
class StreamSystemErrTest extends CamelTestSupport {

    @Test
    void testStringContent() {
        Exchange result = template.send("direct:in", exchange -> exchange.getIn().setBody("Hello Text World\n"));
        assertNotNull(result);
        assertFalse(result.isFailed(), "Sending string content to stream:err should not cause an exchange failure");
        assertNotNull(result.getIn().getBody(), "Exchange body should be preserved after sending");
    }

    @Test
    void testBinaryContent() {
        Exchange result = template.send("direct:in", exchange -> exchange.getIn().setBody("Hello Bytes World\n".getBytes()));
        assertNotNull(result);
        assertFalse(result.isFailed(), "Sending binary content to stream:err should not cause an exchange failure");
        assertNotNull(result.getIn().getBody(), "Exchange body should be preserved after sending");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("stream:err");
            }
        };
    }

}
