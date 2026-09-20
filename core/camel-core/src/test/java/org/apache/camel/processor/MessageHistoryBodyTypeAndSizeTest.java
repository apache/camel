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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.MessageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message history carries the body's type and size as each node was reached (CAMEL-24844): what a person or a model
 * needs to see where the body changed from text into a Map or bytes, on every message and without the tracer.
 */
public class MessageHistoryBodyTypeAndSizeTest extends ContextTestSupport {

    @Test
    public void testBodyTypeAndSizePerStep() throws Exception {
        context.getMessageSizeStrategy().setEnabled(true);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("Hello World"));
        assertMockEndpointsSatisfied();

        List<MessageHistory> history = out.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        assertNotNull(history);
        assertEquals(4, history.size());

        // as each step was reached: the text sent, the bytes the first step made, the list the second step made
        assertEquals("a", history.get(0).getNode().getId());
        assertEquals("java.lang.String", history.get(0).getBodyType());
        assertEquals(11, history.get(0).getBodySize());

        assertEquals("b", history.get(1).getNode().getId());
        assertEquals("byte[]", history.get(1).getBodyType());
        assertEquals(11, history.get(1).getBodySize());

        assertEquals("c", history.get(2).getNode().getId());
        assertEquals("java.util.ArrayList", history.get(2).getBodyType());
        assertEquals(3, history.get(2).getBodySize(), "the size strategy counts the elements of a collection");

        // a null body is shown as such, not left blank like a body that was not captured
        assertEquals("d", history.get(3).getNode().getId());
        assertEquals("null", history.get(3).getBodyType());
        assertEquals(-1, history.get(3).getBodySize(), "no body, no size");

        // the failure table shows both columns
        String table = MessageHelper.dumpMessageHistoryStacktrace(out, null, false);
        assertTrue(table.contains("Body type"), table);
        assertTrue(table.contains("java.lang.String"), table);
        assertTrue(table.contains("byte[]"), table);
        assertTrue(table.contains("java.util.ArrayList"), table);
        assertTrue(table.contains("null"), table);
        // the route's own row (the body as it is now, null after step c) shows no size either
        // (the row is found by its from[] label as the route id is auto assigned and depends on the JVM's test order)
        String routeRow = table.lines().filter(l -> l.contains("from[direct://start]")).findFirst().orElse("");
        assertTrue(routeRow.contains("null") && !routeRow.trim().endsWith("0"), "route row: " + routeRow);
    }

    @Test
    public void testTypeCapturedSizeNotWhenTheStrategyIsOff() throws Exception {
        // the default outside the dev profile: the type is always captured (one class lookup), the size is not
        context.getMessageSizeStrategy().setEnabled(false);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("Hello World"));
        assertMockEndpointsSatisfied();

        List<MessageHistory> history = out.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        assertEquals(4, history.size());
        assertEquals("java.lang.String", history.get(0).getBodyType());
        assertEquals("byte[]", history.get(1).getBodyType());
        assertEquals("null", history.get(3).getBodyType());
        for (MessageHistory h : history) {
            assertEquals(-1, h.getBodySize(), h.getNode().getId());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);
                context.getMessageHistoryFactory().setNodePattern("step");
                from("direct:start")
                        .step("a").convertBodyTo(byte[].class).end()
                        .step("b").transform().constant(new ArrayList<>(List.of(1, 2, 3))).end()
                        .step("c").process(e -> e.getMessage().setBody(null)).end()
                        .step("d").to("mock:result").end();
            }
        };
    }
}
