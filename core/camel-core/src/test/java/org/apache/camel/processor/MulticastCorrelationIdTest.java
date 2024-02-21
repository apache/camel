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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MulticastCorrelationIdTest extends ContextTestSupport {

    @Test
    void testCorrelationIdIsNotOverwrittenByMulticast() {
        String originalCorrelationId = "SOME_ID";
        Exchange exchange
                = template.request("direct:start", e -> e.setProperty(Exchange.CORRELATION_ID, originalCorrelationId));

        assertEquals(originalCorrelationId, exchange.getProperty(Exchange.CORRELATION_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").multicast().to("direct:a", "direct:b");
                from("direct:a").log("Route a");
                from("direct:b").log("Route b");
            }
        };
    }

}
