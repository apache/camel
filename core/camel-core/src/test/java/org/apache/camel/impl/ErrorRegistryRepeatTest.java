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
package org.apache.camel.impl;

import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24911: a storm of the same error keeps only a few exchanges but keeps counting, and does not push the other
 * errors out of the registry.
 */
public class ErrorRegistryRepeatTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        context.getErrorRegistry().setMaximumEntries(10);
        return context;
    }

    @Test
    public void testStormIsCountedAndDoesNotEvictTheOtherError() throws Exception {
        template.sendBody("direct:other", "Hello");

        for (int i = 0; i < 30; i++) {
            template.sendBody("direct:storm", "Hello " + i);
        }

        Collection<BacklogErrorEventMessage> entries = context.getErrorRegistry().browse();
        List<BacklogErrorEventMessage> storm
                = entries.stream().filter(e -> "storm".equals(e.getRouteId())).toList();
        List<BacklogErrorEventMessage> other
                = entries.stream().filter(e -> "other".equals(e.getRouteId())).toList();

        assertEquals(3, storm.size(), "only the newest exchanges of the storm are kept: " + entries);
        assertEquals(1, other.size(), "the other error must survive the storm: " + entries);

        // the newest entry is first, and its counter has kept rising past the entries that were evicted
        BacklogErrorEventMessage newest = storm.get(0);
        assertEquals(30, newest.getRepeatCount());
        assertEquals(29, storm.get(1).getRepeatCount(), "the 3 kept entries are the newest: 30, 29, 28");
        assertEquals(1, other.get(0).getRepeatCount(), "an error that happened once is not a repeat");
        assertTrue(newest.getRepeatFirstTimestamp() <= newest.getRepeatLastTimestamp());
        assertEquals(newest.getTimestamp(), newest.getRepeatLastTimestamp());
    }

    @Test
    public void testAStormWhoseMessagesDifferIsStillOneKind() throws Exception {
        template.sendBody("direct:other", "Hello");

        // a real storm carries the failing payload in its message, so no two messages are the same
        for (int i = 0; i < 20; i++) {
            template.sendBody("direct:payload", "sku-" + i);
        }

        Collection<BacklogErrorEventMessage> entries = context.getErrorRegistry().browse();
        List<BacklogErrorEventMessage> storm
                = entries.stream().filter(e -> "payload".equals(e.getRouteId())).toList();

        assertEquals(3, storm.size(), "the kind is the route, node and exception type, not the message: " + entries);
        assertEquals(20, storm.get(0).getRepeatCount());
        assertEquals("unknown sku sku-19", storm.get(0).getExceptionMessage(), "the messages are still readable");
        assertEquals(1, entries.stream().filter(e -> "other".equals(e.getRouteId())).count(),
                "the other error must survive the storm: " + entries);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(0));

                from("direct:storm").routeId("storm")
                        .throwException(new IllegalArgumentException("Forced error"));

                from("direct:payload").routeId("payload")
                        .process(e -> {
                            throw new IllegalArgumentException("unknown sku " + e.getMessage().getBody(String.class));
                        });

                from("direct:other").routeId("other")
                        .throwException(new IllegalStateException("Something else"));
            }
        };
    }
}
