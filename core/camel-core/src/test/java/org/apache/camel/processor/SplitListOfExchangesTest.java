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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Splitting a body of type {@code List<Exchange>}, as produced by batching consumers and the grouped exchange
 * aggregation strategy, should unwrap each part into the child exchange, like splitting a {@code List<Message>} does.
 * Exchange pooling is enabled because a pooled child exchange resets its message on completion, which would wipe the
 * parts if their message was adopted instead of copied.
 */
public class SplitListOfExchangesTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        return camelContext;
    }

    @Test
    public void testSplitListOfExchanges() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("A", "B", "C");
        mock.expectedHeaderValuesReceivedInAnyOrder("foo", 1, 2, 3);

        List<Exchange> parts = createParts();
        template.sendBody("direct:start", parts);

        assertMockEndpointsSatisfied();

        for (Exchange child : mock.getReceivedExchanges()) {
            assertEquals("bar", child.getMessage().getHeader("common"));
            // the part is unwrapped, the child body is not the part exchange itself
            assertNull(child.getMessage().getBody(Exchange.class));
            // exchange properties of the part are not carried over (same as for Message parts)
            assertNull(child.getProperty("partProperty"));
        }

        // the owner of the parts (e.g. a batching consumer committing after the route) still sees them intact
        for (int i = 0; i < parts.size(); i++) {
            Message part = parts.get(i).getMessage();
            assertEquals(List.of("A", "B", "C").get(i), part.getBody());
            assertEquals(i + 1, part.getHeader("foo"));
            assertEquals("bar", part.getHeader("common"));
        }
    }

    private List<Exchange> createParts() {
        List<Exchange> parts = new ArrayList<>();
        int i = 1;
        for (String body : List.of("A", "B", "C")) {
            Exchange part = new DefaultExchange(context);
            Message message = part.getMessage();
            message.setBody(body);
            message.setHeader("foo", i++);
            message.setHeader("common", "bar");
            part.setProperty("partProperty", "notCopied");
            parts.add(part);
        }
        return parts;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .split(body())
                        .to("mock:split");
            }
        };
    }
}
