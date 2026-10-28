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
package org.apache.camel.impl.console;

import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel Main sets {@code camel.devConsole.<id>.capacity} on a console the registry has already started, so the console
 * must resize the buffers it allocated with the default capacity.
 */
public class DevConsoleCapacityTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setBacklogTracing(true);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").log("${body}").to("mock:result");
            }
        };
    }

    private <T extends DevConsole> T resolve(String id) {
        // the registry starts the console, as when Camel Main configures it
        return (T) context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class).resolveById(id);
    }

    private void sendMessages(String uri, int count) {
        for (int i = 0; i < count; i++) {
            template.sendBody(uri, "Hello " + i);
        }
    }

    @Test
    public void testTraceCapacitySetAfterStart() {
        TraceDevConsole console = resolve("trace");
        console.setCapacity(1000);

        // each dump takes up to 100 events from the tracer (its backlog size): the second one used to fail with
        // "Queue full" as the queue still had the default 100 slots
        int traces = 0;
        for (int round = 0; round < 3; round++) {
            sendMessages("direct:start", 50);
            JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));
            traces = out.getCollection("traces").size();
        }
        assertEquals(300, traces);
    }

    @Test
    public void testTraceCapacityLoweredAfterStart() {
        TraceDevConsole console = resolve("trace");
        sendMessages("direct:start", 50);
        console.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));

        console.setCapacity(50);
        sendMessages("direct:start", 50);
        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));
        assertEquals(50, out.getCollection("traces").size());
    }

    @Test
    public void testReceiveCapacitySetAfterStart() {
        ReceiveDevConsole console = resolve("receive");
        console.setCapacity(1000);
        console.call(DevConsole.MediaType.JSON, Map.of("enabled", "true", "endpoint", "direct:incoming"));

        sendMessages("direct:incoming", 150);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("dump", "true"));
        List<JsonObject> messages = out.getCollection("messages");
        assertEquals(150, messages.size());
        assertEquals(150, messages.get(149).getLong("uid"));
    }

    @Test
    public void testEventCapacitySetAfterStart() {
        EventConsole console = resolve("event");
        console.setCapacity(1000);

        // used to fail with ArrayIndexOutOfBoundsException once past the default 25 slots
        sendMessages("direct:start", 50);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertTrue(out.getCollection("exchangeEvents").size() > 25);
        assertTrue(((String) console.call(DevConsole.MediaType.TEXT)).contains("Exchange Events"));
    }

    @Test
    public void testEventCapacityLoweredAfterStart() {
        EventConsole console = resolve("event");
        console.setCapacity(100);
        sendMessages("direct:start", 30);

        console.setCapacity(25);
        sendMessages("direct:start", 30);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertEquals(25, out.getCollection("exchangeEvents").size());
    }

    @Test
    public void testSqlTraceCapacitySetAfterStart() {
        SqlTraceDevConsole console = resolve("sql-trace");
        console.setCapacity(1000);

        // used to fail with ArrayIndexOutOfBoundsException as the dump walked 1000 slots of the default 200
        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertTrue(out.isEmpty());
        assertEquals("", console.call(DevConsole.MediaType.TEXT));
    }

    @Test
    public void testInvalidCapacitySetAfterStart() {
        // the range checked when the console starts is also checked when the capacity is set afterwards
        TraceDevConsole trace = resolve("trace");
        assertInvalidCapacity(trace::getCapacity, trace::setCapacity, 50);
        ReceiveDevConsole receive = resolve("receive");
        assertInvalidCapacity(receive::getCapacity, receive::setCapacity, 50);
        EventConsole event = resolve("event");
        assertInvalidCapacity(event::getCapacity, event::setCapacity, 25);
        SqlTraceDevConsole sqlTrace = resolve("sql-trace");
        assertInvalidCapacity(sqlTrace::getCapacity, sqlTrace::setCapacity, 25);
    }

    private static void assertInvalidCapacity(IntSupplier getter, IntConsumer setter, int min) {
        int before = getter.getAsInt();
        for (int capacity : new int[] { min - 1, 1001 }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> setter.accept(capacity));
            assertEquals("Capacity must be between " + min + " and 1000", e.getMessage());
            assertEquals(before, getter.getAsInt());
        }
    }
}
